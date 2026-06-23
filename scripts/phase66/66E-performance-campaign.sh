#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE66_ROOT"
phase66_setup "66E" "Performance and capacity campaign"
STATUS="FAIL"; MESSAGE="performance campaign failed"
trap 'code=$?; phase66_finalize "$STATUS" "$code" "$MESSAGE"' EXIT
phase66_require_file config/phase66/performance-thresholds.yaml
if ! phase66_is_full; then
  phase66_run "normalize synthetic k6 contract" python3 scripts/phase66/normalize_k6_results.py --self-test
  STATUS="PREPARED"; MESSAGE="load scenarios and strict SLA normalization are ready; no load generated"; exit 0
fi
phase66_require_uat; phase66_require_load_confirmation; phase66_require_command docker
: "${BASE_URL:?BASE_URL required}"
mkdir -p "$PHASE66_PHASE_DIR/raw"
scenarios=(smoke sustained-2k-tps sustained-10k-tps burst-20k-tps soak-8h)
for s in "${scenarios[@]}"; do
  export RESULT_DIR="$PHASE66_PHASE_DIR/raw" RUN_ID="$s"
  phase66_capture "k6 $s" "$PHASE66_PHASE_DIR/$s.log" performance/scripts/run-k6.sh "$s"
  summary=$(find "$RESULT_DIR/$RUN_ID" -maxdepth 1 -type f -name "$s-*.json" -print | sort | tail -1)
  [[ -n "$summary" ]] || { phase66_log "ERROR summary export missing for $s"; exit 2; }
  cp "$summary" "$PHASE66_PHASE_DIR/raw/$s.json"
done
unset RUN_ID
export RESULT_DIR="$PHASE66_PHASE_DIR/raw/settlement"
export SETTLEMENT_SUMMARY_OUTPUT="$PHASE66_PHASE_DIR/raw/settlement-500k.json"
phase66_capture "settlement 500k benchmark" "$PHASE66_PHASE_DIR/settlement-500k.log" performance/settlement/run_settlement_benchmark.sh
phase66_run "normalize and enforce thresholds" python3 scripts/phase66/normalize_k6_results.py \
  --thresholds config/phase66/performance-thresholds.yaml --input-dir "$PHASE66_PHASE_DIR/raw" --output "$PHASE66_PHASE_DIR/performance-summary.json"
STATUS="PASS"; MESSAGE="all required performance scenarios and settlement benchmark met strict thresholds"
