#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--kind", required=True, choices=["phase61", "runtime"])
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--reference")
    parser.add_argument("--commit", required=True)
    parser.add_argument("--application-digest", required=True)
    parser.add_argument("--migration-digest")
    args = parser.parse_args()
    data = json.loads(args.manifest.read_text(encoding="utf-8"))
    errors: list[str] = []
    if args.kind == "phase61":
        if data.get("status") != "PASS":
            errors.append("Phase 61 manifest status is not PASS")
        if data.get("gitCommit") != args.commit:
            errors.append("Phase 61 git commit mismatch")
        if data.get("applicationImageDigest") != args.application_digest:
            errors.append("Phase 61 application image digest mismatch")
        if args.migration_digest and data.get("migrationImageDigest") != args.migration_digest:
            errors.append("Phase 61 migration image digest mismatch")
    else:
        release = data.get("release") or {}
        if release.get("gitCommit") != args.commit:
            errors.append("runtime evidence git commit mismatch")
        if release.get("imageDigest") != args.application_digest:
            errors.append("runtime evidence image digest mismatch")
        if args.reference and release.get("reference") != args.reference:
            errors.append("runtime evidence release reference mismatch")
        if data.get("goLiveReady") is not True:
            errors.append("runtime evidence is not Go-Live ready")
    if errors:
        for error in errors:
            print(f"ERROR: {error}")
        return 1
    print(f"Release identity verified: {args.kind}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
