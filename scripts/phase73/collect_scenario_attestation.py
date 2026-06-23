#!/usr/bin/env python3
import argparse
import datetime as dt
import json
import pathlib

parser = argparse.ArgumentParser()
parser.add_argument("--scenario", required=True)
parser.add_argument("--run-id", required=True)
parser.add_argument("--evidence-dir", required=True)
parser.add_argument("--started-at", required=True)
parser.add_argument("--finished-at", required=True)
parser.add_argument("--recovery-seconds", type=int, required=True)
parser.add_argument("--cleanup-status", choices=["PASS", "FAIL"], required=True)
parser.add_argument("--financial-integrity", required=True)
parser.add_argument("--output", required=True)
args = parser.parse_args()
root = pathlib.Path(args.evidence_dir)
reconciliation = json.loads((root / "reconciliation.json").read_text(encoding="utf-8"))
replay = json.loads((root / "replay-integrity.json").read_text(encoding="utf-8"))
financial = json.loads(pathlib.Path(args.financial_integrity).read_text(encoding="utf-8"))
violations = reconciliation.get("violations") or []
values = {
    "dataLossCount": len(violations),
    "duplicateReplayCount": int(replay.get("newDuplicateReplayCount", 0)),
    "balanceMismatchCount": int(financial.get("balanceMismatchCount", 0)),
    "outboxBacklogGrowth": int(financial.get("outboxBacklogGrowth", 0)),
}
passed = args.cleanup_status == "PASS" and all(v == 0 for v in values.values())
artifacts = [str(p.relative_to(root)) for p in sorted(root.rglob("*")) if p.is_file()]
doc = {
    "schemaVersion": 1,
    "runId": args.run_id,
    "scenario": args.scenario,
    "status": "PASS" if passed else "FAIL",
    "startedAt": args.started_at,
    "finishedAt": args.finished_at,
    "recoveryTimeSeconds": args.recovery_seconds,
    "cleanupStatus": args.cleanup_status,
    "integrity": values,
    "artifacts": artifacts,
}
path = pathlib.Path(args.output)
path.write_text(json.dumps(doc, indent=2, sort_keys=True) + "\n", encoding="utf-8")
print(json.dumps(doc, sort_keys=True))
if not passed:
    raise SystemExit(1)
