#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--phase", required=True)
    parser.add_argument("--name", required=True)
    parser.add_argument("--status", required=True, choices=["PASS", "FAIL", "PREPARED", "SKIPPED"])
    parser.add_argument("--exit-code", type=int, required=True)
    parser.add_argument("--started-at", required=True)
    parser.add_argument("--finished-at", required=True)
    parser.add_argument("--message", default="")
    parser.add_argument("--run-dir", type=Path, required=True)
    parser.add_argument("--phase-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    if not args.phase.startswith("64") or len(args.phase) != 3:
        raise SystemExit("invalid Phase 64 id")
    run_root = args.run_dir.resolve()
    phase_root = args.phase_dir.resolve()
    phase_root.relative_to(run_root)
    artifacts = []
    for path in sorted(phase_root.rglob("*")):
        if not path.is_file() or path.is_symlink() or path.resolve() == args.output.resolve():
            continue
        artifacts.append({
            "path": path.resolve().relative_to(run_root).as_posix(),
            "sizeBytes": path.stat().st_size,
            "sha256": sha256(path),
        })
    document = {
        "schemaVersion": 1,
        "phase": args.phase,
        "name": args.name,
        "status": args.status,
        "exitCode": args.exit_code,
        "message": args.message,
        "startedAt": args.started_at,
        "finishedAt": args.finished_at,
        "targetEnvironment": os.getenv("TARGET_ENVIRONMENT", "repository"),
        "release": {
            "reference": os.getenv("RELEASE_REFERENCE"),
            "gitCommit": os.getenv("RELEASE_GIT_COMMIT") or os.getenv("GITHUB_SHA"),
            "applicationImageDigest": os.getenv("APPLICATION_IMAGE_DIGEST"),
            "migrationImageDigest": os.getenv("MIGRATION_IMAGE_DIGEST"),
        },
        "artifacts": artifacts,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(document, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
