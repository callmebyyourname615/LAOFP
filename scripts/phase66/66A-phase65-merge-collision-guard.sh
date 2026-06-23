#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE66_ROOT"
phase66_setup "66A" "Phase 65 merge and collision guard"
STATUS="FAIL"; MESSAGE="repository boundary validation failed"
trap 'code=$?; phase66_finalize "$STATUS" "$code" "$MESSAGE"' EXIT
phase66_run "static Phase 66 contract" python3 scripts/verify_phase66_static.py
guard_args=(--root . --contract config/phase66/file-boundary.yaml --output "$PHASE66_PHASE_DIR/collision-report.json")
[[ -n "${PHASE65_BASELINE_COMMIT:-}" ]] && guard_args+=(--phase65-baseline "$PHASE65_BASELINE_COMMIT")
phase66_run "capture repository baseline" python3 scripts/phase66/phase65_collision_guard.py "${guard_args[@]}"
if phase66_is_full; then
  phase66_require_uat
  python3 - "$PHASE66_PHASE_DIR/collision-report.json" <<'PY'
import json,sys
r=json.load(open(sys.argv[1]))
if not r["phase65Detected"]: raise SystemExit("Phase 65 baseline/artifacts not detected; merge Phase 65 before full execution")
if r["violations"]: raise SystemExit("changed-file boundary violations exist")
PY
  STATUS="PASS"; MESSAGE="Phase 65 handoff detected and Phase 66 changed-file boundary is clean"
else
  STATUS="PREPARED"; MESSAGE="collision guard ready; full mode will require a detected Phase 65 handoff"
fi
