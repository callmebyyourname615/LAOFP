#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
p67_require_identity
PHASE_ID=67H
p67_require_environment production
p67_require_production_confirmation
p67_begin 67H "Command Center Evidence Recorder"
failed=0
timeline="$PHASE_DIR/command-center-timeline.jsonl"
if [[ "$PHASE67_MODE" == preflight ]]; then
  p67_run_check hash-chain-roundtrip bash -c '
    python3 scripts/phase67/phase67_control.py event-append --timeline "$1" --event-type GATE_OPENED --actor preflight --message "synthetic command-center event" --reference "$2" >/dev/null
    python3 scripts/phase67/phase67_control.py event-verify --timeline "$1" --output "$3" >/dev/null
    exit 3
  ' _ "$timeline" "$RELEASE_REFERENCE" "$PHASE_DIR/timeline-verification.json" || failed=1
else
  p67_require_phase67_pass 67A 67B 67C 67D 67E 67F 67G
  p67_run_check phase55f-pass p67_require_phase55_pass 55F || failed=1
  source_timeline="${PHASE67_COMMAND_CENTER_TIMELINE:-}"
  if [[ -n "$source_timeline" ]]; then
    [[ -f "$source_timeline" && ! -L "$source_timeline" ]] || p67_die "unsafe command-center timeline"
    cp "$source_timeline" "$timeline"
  fi
  actor="${PHASE67_COMMAND_CENTER_ACTOR:-phase67-automation}"
  message="${PHASE67_COMMAND_CENTER_MESSAGE:-Phase 67 production cutover controls verified}"
  p67_run_check record-gate-event python3 scripts/phase67/phase67_control.py event-append \
    --timeline "$timeline" --event-type GATE_APPROVED --actor "$actor" --message "$message" --reference "$RELEASE_REFERENCE" || failed=1
  p67_run_check verify-event-chain python3 scripts/phase67/phase67_control.py event-verify \
    --timeline "$timeline" --output "$PHASE_DIR/timeline-verification.json" || failed=1
fi
if (( failed )); then p67_write_result FAIL; exit 1; fi
p67_write_result
