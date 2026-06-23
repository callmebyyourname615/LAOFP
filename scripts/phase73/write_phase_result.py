#!/usr/bin/env python3
import argparse
import datetime as dt
import hashlib
import json
import pathlib
import subprocess

parser = argparse.ArgumentParser()
for name in ["phase", "name", "status", "started-at", "finished-at", "run-id", "target-environment", "message", "phase-dir", "output"]:
    parser.add_argument(f"--{name}", required=name not in {"message"})
parser.add_argument("--exit-code", required=True, type=int)
args = parser.parse_args()
phase_dir = pathlib.Path(args.phase_dir)
output = pathlib.Path(args.output)
artifacts = []
for path in sorted(phase_dir.rglob("*")):
    if path.is_file() and path.resolve() != output.resolve():
        artifacts.append({
            "path": str(path.relative_to(phase_dir)),
            "sizeBytes": path.stat().st_size,
            "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        })
try:
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True, stderr=subprocess.DEVNULL).strip()
except Exception:
    commit = "unknown-commit"
result = {
    "schemaVersion": 1,
    "phase": args.phase,
    "name": args.name,
    "status": args.status,
    "exitCode": args.exit_code,
    "message": args.message or "",
    "startedAt": args.started_at,
    "finishedAt": args.finished_at,
    "runId": args.run_id,
    "targetEnvironment": args.target_environment,
    "gitCommit": commit,
    "checks": [{"id": "phase-execution", "status": args.status, "detail": args.message or args.status}],
    "artifacts": artifacts,
}
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
