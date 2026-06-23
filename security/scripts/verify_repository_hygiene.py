#!/usr/bin/env python3
"""Fail closed when prohibited artifacts or high-confidence secrets are tracked.

The scanner intentionally reports only file names, line numbers, and rule IDs.
It never prints the matching secret value.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
from dataclasses import asdict, dataclass
from pathlib import Path, PurePosixPath
from typing import Iterable

SCRIPT_DIR = Path(__file__).resolve().parent
DEFAULT_POLICY = SCRIPT_DIR.parent / "policy" / "repository-hygiene.json"

SECRET_KEY_SUFFIXES = (
    "_PASSWORD",
    "_PASSWD",
    "_PWD",
    "_TOKEN",
    "_SECRET",
    "_API_KEY",
    "_PRIVATE_KEY",
    "_MASTER_KEY",
    "_ACCESS_KEY",
    "_CLIENT_SECRET",
    "_SIGNING_KEY",
    "_CRYPTO_KEY",
    "_KEY_BASE64",
)
SECRET_KEY_EXACT = {"PASSWORD", "PASSWD", "PWD", "TOKEN", "SECRET", "API_KEY"}
ENV_ASSIGNMENT_RE = re.compile(r"^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$")
URI_CREDENTIAL_RE = re.compile(r"(?i)\b(?:jdbc:)?(?:postgresql|postgres|mysql|mongodb|redis)://[^\s:/@]+:[^\s/@]+@")
HIGH_CONFIDENCE_PATTERNS = {
    "private-key-material": re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----"),
    "aws-access-key-id": re.compile(r"\bAKIA[0-9A-Z]{16}\b"),
    "github-token": re.compile(r"\bgh[pousr]_[A-Za-z0-9]{36,255}\b"),
    "slack-token": re.compile(r"\bxox[baprs]-[A-Za-z0-9-]{10,}\b"),
    "credential-in-uri": URI_CREDENTIAL_RE,
    "database-dump-header": re.compile(r"(?i)^--\s+(?:PostgreSQL|MySQL|MariaDB) database dump"),
}
PLACEHOLDER_EXACT = {
    "",
    "''",
    '""',
    "null",
    "none",
    "changeme",
    "change_me",
    "replace_me",
    "redacted",
}
PLACEHOLDER_MARKERS = (
    "${",
    "{{",
    "<",
    "__inject_",
    "__required_",
    "replace_with_",
    "from_vault",
    "from_secret_manager",
    "runtime_secret",
)


@dataclass(frozen=True)
class Finding:
    path: str
    rule: str
    line: int | None = None


def run_git(repo: Path, args: list[str]) -> bytes:
    result = subprocess.run(
        ["git", "-C", str(repo), *args],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if result.returncode != 0:
        message = result.stderr.decode("utf-8", errors="replace").strip()
        raise RuntimeError(message or f"git {' '.join(args)} failed")
    return result.stdout


def git_root(repo: Path) -> Path:
    output = run_git(repo, ["rev-parse", "--show-toplevel"])
    return Path(output.decode().strip()).resolve()


def tracked_paths(repo: Path, staged: bool) -> list[str]:
    if staged:
        raw = run_git(repo, ["diff", "--cached", "--name-only", "--diff-filter=ACMR", "-z"])
    else:
        raw = run_git(repo, ["ls-files", "-z"])
    return sorted({item.decode("utf-8", errors="surrogateescape") for item in raw.split(b"\0") if item})


def is_env_path(path: str) -> bool:
    name = PurePosixPath(path).name
    return name == ".env" or name.startswith(".env.")


def is_allowed_sql(path: str, allowed_prefixes: Iterable[str]) -> bool:
    return any(path.startswith(prefix) for prefix in allowed_prefixes)


def path_findings(paths: list[str], policy: dict) -> list[Finding]:
    findings: list[Finding] = []
    allowed_env = set(policy["allowed_env_paths"])
    forbidden_exact = set(policy["forbidden_exact_paths"])
    forbidden_basenames = set(policy["forbidden_basenames"])
    forbidden_prefixes = tuple(policy["forbidden_path_prefixes"])
    forbidden_suffixes = tuple(policy["forbidden_suffixes"])
    allowed_sql_prefixes = tuple(policy["allowed_sql_prefixes"])

    for path in paths:
        normalized = PurePosixPath(path).as_posix()
        basename = PurePosixPath(normalized).name
        lower = normalized.lower()
        if normalized in forbidden_exact:
            findings.append(Finding(normalized, "forbidden-exact-path"))
        if normalized.startswith(forbidden_prefixes):
            findings.append(Finding(normalized, "forbidden-path-prefix"))
        if basename in forbidden_basenames:
            findings.append(Finding(normalized, "forbidden-basename"))
        if lower.endswith(forbidden_suffixes):
            findings.append(Finding(normalized, "forbidden-file-type"))
        if is_env_path(normalized) and normalized not in allowed_env:
            findings.append(Finding(normalized, "unapproved-env-file"))
        if lower.endswith(".sql") and not is_allowed_sql(normalized, allowed_sql_prefixes):
            findings.append(Finding(normalized, "sql-outside-approved-source-directories"))
    return findings


def is_secret_assignment_key(key: str) -> bool:
    normalized = key.upper()
    return normalized in SECRET_KEY_EXACT or normalized.endswith(SECRET_KEY_SUFFIXES)


def placeholder(value: str) -> bool:
    stripped = value.strip().strip("'\"").strip()
    lowered = stripped.lower()
    if lowered in PLACEHOLDER_EXACT:
        return True
    return any(marker in lowered for marker in PLACEHOLDER_MARKERS)


def text_lines(path: Path, max_bytes: int) -> Iterable[tuple[int, str]]:
    try:
        if path.stat().st_size > max_bytes:
            return []
        data = path.read_bytes()
    except (OSError, PermissionError):
        return []
    if b"\0" in data[:8192]:
        return []
    text = data.decode("utf-8", errors="replace")
    return enumerate(text.splitlines(), start=1)


def content_findings(repo: Path, paths: list[str], policy: dict) -> list[Finding]:
    findings: list[Finding] = []
    ignored = tuple(policy["ignored_content_prefixes"])
    max_bytes = int(policy["max_text_scan_bytes"])
    for rel in paths:
        if rel.startswith(ignored):
            continue
        absolute = repo / rel
        if not absolute.is_file():
            continue
        for line_no, line in text_lines(absolute, max_bytes):
            for rule, pattern in HIGH_CONFIDENCE_PATTERNS.items():
                if pattern.search(line):
                    findings.append(Finding(rel, rule, line_no))
            assignment = ENV_ASSIGNMENT_RE.match(line)
            if assignment:
                key, value = assignment.groups()
                if is_env_path(rel) and is_secret_assignment_key(key) and not placeholder(value):
                    findings.append(Finding(rel, "literal-secret-env-assignment", line_no))
    return findings


def unique_findings(findings: Iterable[Finding]) -> list[Finding]:
    return sorted(set(findings), key=lambda item: (item.path, item.line or 0, item.rule))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", default=".", help="Git working tree to scan")
    parser.add_argument("--policy", default=str(DEFAULT_POLICY), help="Repository hygiene policy JSON")
    parser.add_argument("--staged", action="store_true", help="Scan only staged added/modified files")
    parser.add_argument(
        "--allow-pending-deletions", action="store_true",
        help="Ignore tracked paths that are already deleted from the working tree; intended for pre-commit delivery validation",
    )
    parser.add_argument("--json-report", help="Write a redacted JSON report")
    args = parser.parse_args()

    try:
        repo = git_root(Path(args.repo).resolve())
        policy = json.loads(Path(args.policy).read_text(encoding="utf-8"))
        paths = tracked_paths(repo, args.staged)
        pending_deletions: list[str] = []
        if args.allow_pending_deletions and not args.staged:
            pending_deletions = [path for path in paths if not (repo / path).exists()]
            paths = [path for path in paths if path not in set(pending_deletions)]
        findings = unique_findings(path_findings(paths, policy) + content_findings(repo, paths, policy))
    except (OSError, RuntimeError, ValueError, json.JSONDecodeError) as exc:
        print(f"repository hygiene scanner error: {exc}", file=sys.stderr)
        return 2

    report = {
        "schema_version": 1,
        "repository": repo.name,
        "scope": "staged" if args.staged else "tracked",
        "files_scanned": len(paths),
        "pending_deletions_ignored": pending_deletions,
        "finding_count": len(findings),
        "findings": [asdict(item) for item in findings],
    }
    if args.json_report:
        report_path = Path(args.json_report)
        report_path.parent.mkdir(parents=True, exist_ok=True)
        report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        try:
            os.chmod(report_path, 0o600)
        except OSError:
            pass

    if findings:
        print(f"Repository hygiene FAILED: {len(findings)} finding(s).", file=sys.stderr)
        for item in findings:
            location = f"{item.path}:{item.line}" if item.line else item.path
            print(f"  - {location} [{item.rule}]", file=sys.stderr)
        print("No secret values were printed. See docs/security/REPOSITORY_SECURITY_CLEANUP.md.", file=sys.stderr)
        return 1

    print(f"Repository hygiene passed: {len(paths)} file(s) scanned.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
