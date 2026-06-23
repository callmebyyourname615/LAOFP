#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE60_ROOT"
phase_setup "60H" "Performance and capacity evidence"
PHASE_STATUS="FAIL"
PHASE_MESSAGE="performance or capacity certification failed"
trap 'code=$?; phase_finalize "$PHASE_STATUS" "$code" "$PHASE_MESSAGE"' EXIT

for scenario in smoke sustained-2k-tps sustained-10k-tps burst-20k-tps soak-8h; do
  phase_require_file "performance/scenarios/$scenario.js"
done
phase_require_file performance/settlement/run_settlement_benchmark.sh
phase_require_file performance/scripts/capture-capacity-evidence.sh
phase_require_file scripts/phase60/verify_performance_evidence.py

if phase_is_preflight; then
  PHASE_STATUS="PREPARED"
  PHASE_MESSAGE="100/2k/10k/20k/8h and settlement-500k runners plus evidence verifier are ready"
  exit 0
fi

phase_require_runtime_confirmation
phase_require_command docker
phase_require_command kubectl
phase_require_command psql
: "${BASE_URL:?BASE_URL is required}"
: "${API_KEY:?API_KEY is required}"
: "${DB_URL:?DB_URL is required}"
: "${DB_USERNAME:?DB_USERNAME is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"
: "${PERFORMANCE_ATTESTATION:?PERFORMANCE_ATTESTATION must point to the completed capacity attestation}"

export RESULT_DIR="$PHASE60_PHASE_DIR/k6"
mkdir -p "$RESULT_DIR"
phase_run "capacity evidence before load" env RESULT_DIR="$PHASE60_PHASE_DIR/capacity-before" \
  performance/scripts/capture-capacity-evidence.sh

for scenario in smoke sustained2k sustained10k burst20k soak8h; do
  phase_run "k6 scenario $scenario" performance/scripts/run-k6.sh "$scenario"
done

phase_run "capacity evidence after load" env RESULT_DIR="$PHASE60_PHASE_DIR/capacity-after" \
  performance/scripts/capture-capacity-evidence.sh

export SETTLEMENT_TX_COUNT=500000
export SETTLEMENT_SUMMARY_OUTPUT="$PHASE60_PHASE_DIR/settlement-500k-summary.json"
phase_run "settlement 500k benchmark" performance/settlement/run_settlement_benchmark.sh

phase_run "financial reconciliation after performance run" \
  performance/scripts/reconcile-performance-data.sh "$PHASE60_PHASE_DIR/performance-reconciliation.csv"

phase_run "performance evidence verification" python3 scripts/phase60/verify_performance_evidence.py \
  --result-dir "$PHASE60_PHASE_DIR/k6" \
  --settlement-summary "$PHASE60_PHASE_DIR/settlement-500k-summary.json" \
  --attestation "$PERFORMANCE_ATTESTATION" \
  --output "$PHASE60_PHASE_DIR/performance-evidence.json"

PHASE_STATUS="PASS"
PHASE_MESSAGE="10k sustained, 20k burst, 8h soak, capacity safeguards and settlement 500k evidence passed"
