#!/usr/bin/env python3
"""Fail closed when runtime certification evidence appears to contain secrets.

Only file names, line numbers, and rule IDs are reported. Matching values are
never printed. Binary files and large files are skipped and remain protected by
manifest hashes.
"""
from __future__ import annotations

import argparse
import json
import pathlib
import re

PATTERNS = {
    "private-key-material": re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----"),
    "authorization-bearer": re.compile(r"(?i)authorization\s*[:=]\s*bearer\s+[A-Za-z0-9._~+/=-]{12,}"),
    "credential-in-uri": re.compile(r"(?i)\b(?:jdbc:)?(?:postgresql|postgres|mysql|mongodb|redis)://[^\s:/@]+:[^\s/@]+@"),
    "aws-access-key-id": re.compile(r"\bAKIA[0-9A-Z]{16}\b"),
    "github-token": re.compile(r"\bgh[pousr]_[A-Za-z0-9]{36,255}\b"),
    "slack-token": re.compile(r"\bxox[baprs]-[A-Za-z0-9-]{10,}\b"),
}
TEXT_SUFFIXES = {
    ".txt", ".log", ".json", ".jsonl", ".xml", ".yaml", ".yml", ".md", ".csv", ".sql", ".properties"
}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--max-bytes", type=int, default=5 * 1024 * 1024)
    args = parser.parse_args()
    root = pathlib.Path(args.root).resolve()
    findings = []
    scanned = 0
    for path in sorted(root.rglob("*")):
        if not path.is_file() or path.is_symlink() or path.suffix.lower() not in TEXT_SUFFIXES:
            continue
        if path.stat().st_size > args.max_bytes:
            continue
        data = path.read_bytes()
        if b"\0" in data[:8192]:
            continue
        scanned += 1
        for number, line in enumerate(data.decode("utf-8", errors="replace").splitlines(), 1):
            for rule, pattern in PATTERNS.items():
                if pattern.search(line):
                    findings.append({"path": str(path.relative_to(root)), "line": number, "rule": rule})
    report = {"schemaVersion": 1, "filesScanned": scanned, "findingCount": len(findings), "findings": findings, "passed": not findings}
    output = pathlib.Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if findings:
        print(f"Certification evidence secret scan FAILED: {len(findings)} finding(s).", flush=True)
        for item in findings:
            print(f"  - {item['path']}:{item['line']} [{item['rule']}]", flush=True)
        return 1
    print(f"Certification evidence secret scan passed: {scanned} text file(s) scanned.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
