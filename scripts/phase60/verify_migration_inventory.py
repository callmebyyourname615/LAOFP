#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import re
from datetime import datetime, timezone
from pathlib import Path

EXPECTED_COUNT = 95
EXPECTED_LATEST = 100
RESERVED_GAPS = [88, 89, 90, 98, 99]


def digest(path: Path) -> str:
    value = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(chunk)
    return value.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=".")
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    root = Path(args.root).resolve()
    migration_dir = root / "src/main/resources/db/migration"

    errors: list[str] = []
    by_version: dict[int, list[Path]] = {}
    for path in migration_dir.glob("V*__*.sql"):
        match = re.fullmatch(r"V(\d+)__(.+)\.sql", path.name)
        if not match:
            errors.append(f"invalid migration filename: {path.name}")
            continue
        by_version.setdefault(int(match.group(1)), []).append(path)

    duplicates = sorted(version for version, files in by_version.items() if len(files) != 1)
    versions = sorted(by_version)
    missing = sorted(set(range(1, EXPECTED_LATEST + 1)) - set(versions))
    if duplicates:
        errors.append(f"duplicate migration versions: {duplicates}")
    if len(versions) != EXPECTED_COUNT:
        errors.append(f"migration count {len(versions)} != {EXPECTED_COUNT}")
    if not versions or versions[-1] != EXPECTED_LATEST:
        errors.append(f"latest migration {versions[-1] if versions else None} != {EXPECTED_LATEST}")
    if missing != RESERVED_GAPS:
        errors.append(f"missing versions {missing} != reserved gaps {RESERVED_GAPS}")

    entries = []
    for version in versions:
        path = by_version[version][0]
        entries.append({
            "version": version,
            "file": path.relative_to(root).as_posix(),
            "sha256": digest(path),
            "sizeBytes": path.stat().st_size,
        })

    document = {
        "schemaVersion": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "status": "PASS" if not errors else "FAIL",
        "expectedCount": EXPECTED_COUNT,
        "actualCount": len(versions),
        "latestVersion": versions[-1] if versions else None,
        "reservedGaps": RESERVED_GAPS,
        "errors": errors,
        "migrations": entries,
    }
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(document, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"Migration inventory: {document['status']} ({len(versions)} files, latest V{document['latestVersion']})")
    for error in errors:
        print(f"  ERROR: {error}")
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
