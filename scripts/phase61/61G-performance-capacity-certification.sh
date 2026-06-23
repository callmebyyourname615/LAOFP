#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE61_ROOT"
phase_setup "61G" "Performance and capacity certification"
PHASE_STATUS="FAIL"; PHASE_MESSAGE="performance/capacity certification failed"
trap 'code=$?; phase_finalize "$PHASE_STATUS" "$code" "$PHASE_MESSAGE"' EXIT
for file in performance/scenarios/{smoke,sustained-2k-tps,sustained-10k-tps,burst-20k-tps,soak-8h}.js; do phase_require_file "$file"; done
phase_require_file scripts/phase61/verify_capacity_attestation.py
if phase_is_preflight; then
  PHASE_STATUS="PREPARED"; PHASE_MESSAGE="100/2k/10k/20k/8h load, infrastructure metrics and signed capacity verification are ready"; exit 0
fi
phase_require_uat_confirmation
: "${BASE_URL:?BASE_URL is required}"; : "${API_KEY:?API_KEY is required}"
: "${CAPACITY_ATTESTATION:?CAPACITY_ATTESTATION is required}"
export RESULT_DIR="$PHASE61_PHASE_DIR/k6"; mkdir -p "$RESULT_DIR"
phase_run "capacity metrics before load" env RESULT_DIR="$PHASE61_PHASE_DIR/capacity-before" performance/scripts/capture-capacity-evidence.sh
for scenario in smoke sustained2k sustained10k burst20k soak8h; do phase_run "k6 $scenario" performance/scripts/run-k6.sh "$scenario"; done
phase_run "capacity metrics after load" env RESULT_DIR="$PHASE61_PHASE_DIR/capacity-after" performance/scripts/capture-capacity-evidence.sh
phase_run "capacity evidence verification" python3 scripts/phase61/verify_capacity_attestation.py \
  --result-dir "$RESULT_DIR" \
  --before-dir "$PHASE61_PHASE_DIR/capacity-before" \
  --after-dir "$PHASE61_PHASE_DIR/capacity-after" \
  --attestation "$CAPACITY_ATTESTATION" \
  --output "$PHASE61_PHASE_DIR/capacity-certification.json"
PHASE_STATUS="PASS"; PHASE_MESSAGE="10k sustained, 20k burst and 8h soak passed without saturation, leak or unbounded lag"
