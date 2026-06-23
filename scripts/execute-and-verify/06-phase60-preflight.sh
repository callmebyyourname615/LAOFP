#!/usr/bin/env bash
# Action #6 — Validate the Phase 60A-60J delivery without contacting UAT.
set -Eeuo pipefail
cd "$(dirname "$0")/../.."

export PHASE60_EVIDENCE_ROOT="${EVIDENCE_DIR:-scripts/execute-and-verify/evidence/manual}/phase60-preflight"
mkdir -p "$PHASE60_EVIDENCE_ROOT"

python3 scripts/verify_phase60_static.py
./scripts/phase60/run_phase60.sh --preflight

latest_run="$(find "$PHASE60_EVIDENCE_ROOT" -mindepth 1 -maxdepth 1 -type d | sort | tail -1)"
[[ -n "$latest_run" ]] || { echo "Phase 60 preflight did not create a run directory"; exit 1; }

python3 - "$latest_run" <<'PY'
import json
import pathlib
import sys

run_dir = pathlib.Path(sys.argv[1])
expected = [f"60{letter}" for letter in "ABCDEFGHIJ"]
issues = []
for phase in expected:
    result_path = run_dir / phase / "result.json"
    if not result_path.is_file():
        issues.append(f"missing {result_path}")
        continue
    result = json.loads(result_path.read_text(encoding="utf-8"))
    if result.get("status") not in {"PREPARED", "PASS"}:
        issues.append(f"{phase}: unexpected status {result.get('status')}")
if issues:
    raise SystemExit("\n".join(issues))
print(f"Phase 60 preflight: PASS ({run_dir.name})")
PY
