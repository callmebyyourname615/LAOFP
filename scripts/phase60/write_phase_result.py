#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import os
import subprocess
from datetime import datetime, timezone
from pathlib import Path


def git(root: Path, *args: str) -> str:
    completed = subprocess.run(
        ["git", *args], cwd=root, text=True, stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL, check=False
    )
    return completed.stdout.strip() if completed.returncode == 0 else "unknown"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--phase", required=True)
    parser.add_argument("--name", required=True)
    parser.add_argument("--status", choices=["PASS", "FAIL", "PREPARED", "SKIPPED"], required=True)
    parser.add_argument("--exit-code", type=int, required=True)
    parser.add_argument("--started-at", required=True)
    parser.add_argument("--finished-at", required=True)
    parser.add_argument("--message", default="")
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--phase-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    root = args.root.resolve()
    phase_dir = args.phase_dir.resolve()
    output = args.output.resolve()
    artifacts = []
    for path in sorted(phase_dir.rglob("*")):
        if not path.is_file() or path.resolve() == output:
            continue
        artifacts.append({
            "path": path.relative_to(root).as_posix() if path.is_relative_to(root) else path.name,
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
        "generatedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "gitCommit": git(root, "rev-parse", "HEAD"),
        "gitTreeState": "dirty" if git(root, "status", "--porcelain") else "clean",
        "targetEnvironment": os.environ.get("TARGET_ENVIRONMENT", "repository"),
        "applicationImageDigest": os.environ.get("APPLICATION_IMAGE_DIGEST"),
        "migrationImageDigest": os.environ.get("MIGRATION_IMAGE_DIGEST"),
        "artifacts": artifacts,
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(document, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
