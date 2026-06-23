#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
from datetime import datetime, timezone
from pathlib import Path

EXPECTED_LATEST_MIGRATION = 100
EXPECTED_MIGRATION_COUNT = 95
RESERVED_MIGRATION_GAPS = {88, 89, 90, 98, 99}
ALLOWED_PENDING_DELETIONS = {".env.bak", "new.txt"}
PROHIBITED_TRACKED_EXACT = {
    ".env", ".env.bak", "new.txt", "app-live.log", "boot.log", "run-error.log"
}
PROHIBITED_TRACKED_SUFFIXES = {
    ".pem", ".key", ".p12", ".pfx", ".jks", ".keystore", ".pgdump", ".backup"
}
REQUIRED_FILES = [
    "pom.xml",
    "src/main/resources/db/migration/V97__smos_user_access_management.sql",
    "src/main/resources/db/migration/V100__repair_current_status_reporting.sql",
    "src/main/java/com/example/switching/usermgmt/service/AuthenticationService.java",
    "src/main/java/com/example/switching/dashboard/settlement/controller/SettlementDashboardController.java",
    "src/main/java/com/example/switching/dashboard/risk/controller/RiskDashboardController.java",
    "src/main/java/com/example/switching/dashboard/crossborder/controller/CrossBorderDashboardController.java",
    "config/production-environment-contract.yaml",
    ".github/workflows/build.yml",
    ".github/workflows/release-evidence.yml",
]


def run(root: Path, *command: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        list(command), cwd=root, text=True, stdout=subprocess.PIPE,
        stderr=subprocess.PIPE, check=False
    )


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=".")
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    root = Path(args.root).resolve()
    output = Path(args.output).resolve()

    errors: list[str] = []
    warnings: list[str] = []
    checks: dict[str, object] = {}

    if run(root, "git", "rev-parse", "--is-inside-work-tree").stdout.strip() != "true":
        errors.append("repository is not a Git work tree")

    status = run(root, "git", "status", "--porcelain=v1").stdout.splitlines()
    deleted = {
        line[3:] for line in status
        if len(line) >= 4 and "D" in line[:2]
    }
    unexpected_deletions = sorted(deleted - ALLOWED_PENDING_DELETIONS)
    if unexpected_deletions:
        errors.append("unexpected tracked deletions: " + ", ".join(unexpected_deletions[:20]))
    checks["pendingDeletions"] = sorted(deleted)

    tracked = set(run(root, "git", "ls-files").stdout.splitlines())
    prohibited: list[str] = []
    for path in sorted(tracked):
        candidate = root / path
        is_pending_allowed_deletion = path in ALLOWED_PENDING_DELETIONS and not candidate.exists()
        if is_pending_allowed_deletion:
            continue
        if path in PROHIBITED_TRACKED_EXACT:
            prohibited.append(path)
            continue
        if path.startswith("target/") or "/target/" in path:
            prohibited.append(path)
            continue
        if path.endswith(".DS_Store") or Path(path).suffix.lower() in PROHIBITED_TRACKED_SUFFIXES:
            prohibited.append(path)
    if prohibited:
        errors.append("prohibited files remain tracked: " + ", ".join(prohibited[:20]))
    checks["prohibitedTrackedFiles"] = prohibited

    diff_check = run(root, "git", "diff", "--check")
    if diff_check.returncode != 0:
        errors.append("git diff --check failed")
        checks["diffCheckOutput"] = diff_check.stdout.strip() or diff_check.stderr.strip()
    else:
        checks["diffCheckOutput"] = "PASS"

    env_path = root / ".env"
    if env_path.exists():
        ignored = run(root, "git", "check-ignore", "-q", ".env").returncode == 0
        if not ignored:
            errors.append(".env exists but is not ignored")
        else:
            warnings.append("local .env exists and is ignored; it is intentionally excluded from delivery artifacts")

    migrations: dict[int, list[Path]] = {}
    migration_dir = root / "src/main/resources/db/migration"
    for path in sorted(migration_dir.glob("V*__*.sql")):
        match = re.fullmatch(r"V(\d+)__.+\.sql", path.name)
        if not match:
            errors.append(f"invalid migration filename: {path.name}")
            continue
        migrations.setdefault(int(match.group(1)), []).append(path)
    duplicates = {version: paths for version, paths in migrations.items() if len(paths) > 1}
    if duplicates:
        errors.append("duplicate Flyway versions: " + ", ".join(map(str, sorted(duplicates))))
    versions = sorted(migrations)
    missing = set(range(1, EXPECTED_LATEST_MIGRATION + 1)) - set(versions)
    if len(versions) != EXPECTED_MIGRATION_COUNT:
        errors.append(f"migration count is {len(versions)}, expected {EXPECTED_MIGRATION_COUNT}")
    if not versions or versions[-1] != EXPECTED_LATEST_MIGRATION:
        errors.append(f"latest migration is {versions[-1] if versions else 'none'}, expected {EXPECTED_LATEST_MIGRATION}")
    if missing != RESERVED_MIGRATION_GAPS:
        errors.append(f"migration gaps are {sorted(missing)}, expected reserved gaps {sorted(RESERVED_MIGRATION_GAPS)}")
    checks["migrationInventory"] = {
        "count": len(versions),
        "latest": versions[-1] if versions else None,
        "reservedGaps": sorted(missing),
        "sha256": {
            f"V{version}": sha256(paths[0])
            for version, paths in sorted(migrations.items()) if len(paths) == 1
        },
    }

    missing_required = [path for path in REQUIRED_FILES if not (root / path).is_file()]
    if missing_required:
        errors.append("required implementation files are missing: " + ", ".join(missing_required))
    checks["requiredFiles"] = {path: (root / path).is_file() for path in REQUIRED_FILES}

    commit = run(root, "git", "rev-parse", "HEAD").stdout.strip() or "unknown"
    document = {
        "schemaVersion": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "gitCommit": commit,
        "status": "PASS" if not errors else "FAIL",
        "errors": errors,
        "warnings": warnings,
        "checks": checks,
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(document, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    print(f"Phase 60A repository baseline: {document['status']}")
    for warning in warnings:
        print(f"  WARN: {warning}")
    for error in errors:
        print(f"  ERROR: {error}")
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
