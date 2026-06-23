#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--run-dir", type=Path, required=True)
    args = parser.parse_args()
    document = json.loads(args.manifest.read_text(encoding="utf-8"))
    errors: list[str] = []

    expected_phases = {f"60{letter}" for letter in "ABCDEFGHIJ"}
    if set(document.get("phases") or {}) != expected_phases:
        errors.append("manifest does not contain exactly Phase 60A through 60J")
    for phase, entry in (document.get("phases") or {}).items():
        if entry.get("status") != "PASS":
            errors.append(f"{phase} is not PASS")
        result = args.run_dir / entry.get("resultPath", "")
        if not result.is_file() or sha256(result) != entry.get("resultSha256"):
            errors.append(f"{phase} result hash does not match")

    seen: set[str] = set()
    for artifact in document.get("artifacts") or []:
        relative = artifact.get("path", "")
        if relative in seen:
            errors.append(f"duplicate artifact path: {relative}")
            continue
        seen.add(relative)
        path = (args.run_dir / relative).resolve()
        if args.run_dir.resolve() not in path.parents:
            errors.append(f"artifact escapes run directory: {relative}")
            continue
        if not path.is_file():
            errors.append(f"artifact is missing: {relative}")
            continue
        if path.stat().st_size != artifact.get("sizeBytes") or sha256(path) != artifact.get("sha256"):
            errors.append(f"artifact integrity mismatch: {relative}")

    if errors:
        print(f"Phase 60 evidence verification: FAIL ({len(errors)} issues)")
        for error in errors:
            print(f"  ERROR: {error}")
        return 1
    print(f"Phase 60 evidence verification: PASS ({len(seen)} artifacts)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
