#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
p67_require_identity
PHASE_ID=67I
p67_require_environment hypercare
p67_begin 67I "Fourteen-Day Hypercare Tracker"
failed=0
if [[ "$PHASE67_MODE" == preflight ]]; then
  p67_run_check synthetic-hypercare python3 scripts/phase67/phase67_control.py hypercare \
    --policy "$PHASE67_POLICY" --input docs/templates/phase67/HYPERCARE_14_DAY.example.json \
    --output "$PHASE_DIR/hypercare-tracker.json" || failed=1
  if (( ! failed )); then
    p67_record_check preflight-boundary PREPARED 0 "phases/$PHASE_ID/logs/synthetic-hypercare.log"
  fi
else
  p67_require_phase67_pass 67A 67B 67C 67D 67E 67F 67G 67H
  p67_run_check phase55i-pass p67_require_phase55_pass 55I || failed=1
  input="${PHASE67_HYPERCARE_CHECKPOINTS_FILE:-$PHASE55_ROOT/phases/55I/phase67-hypercare-checkpoints.json}"
  p67_run_check hypercare-exit python3 scripts/phase67/phase67_control.py hypercare \
    --policy "$PHASE67_POLICY" --input "$input" --output "$PHASE_DIR/hypercare-tracker.json" || failed=1
fi
if (( failed )); then p67_write_result FAIL; exit 1; fi
p67_write_result
