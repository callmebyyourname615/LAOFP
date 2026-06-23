#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("--phase", required=True)
parser.add_argument("--status", required=True, choices=["PASS", "FAIL", "BLOCKED", "PREPARED"])
parser.add_argument("--message", required=True)
parser.add_argument("--mode", required=True, choices=["preflight", "full"])
parser.add_argument("--git-commit", required=True)
parser.add_argument("--output", required=True)
parser.add_argument("--detail", action="append", default=[])
args = parser.parse_args()

details: dict[str, object] = {}
for raw in args.detail:
    if "=" not in raw:
        raise SystemExit(f"invalid --detail: {raw}")
    key, value_raw = raw.split("=", 1)
    if value_raw.lower() in {"true", "false"}:
        value: object = value_raw.lower() == "true"
    else:
        try:
            value = int(value_raw)
        except ValueError:
            try:
                value = float(value_raw)
            except ValueError:
                value = value_raw
    details[key] = value

payload = {
    "schemaVersion": 1,
    "phase": args.phase,
    "status": args.status,
    "mode": args.mode,
    "message": args.message,
    "generatedAt": datetime.now(timezone.utc).isoformat(),
    "gitCommit": args.git_commit,
    "details": details,
}
out = Path(args.output)
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
print(f"{args.phase}: {args.status} - {args.message}")
