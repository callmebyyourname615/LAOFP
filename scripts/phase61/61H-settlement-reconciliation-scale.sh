#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE61_ROOT"
phase_setup "61H" "Settlement and reconciliation scale proof"
PHASE_STATUS="FAIL"; PHASE_MESSAGE="settlement/reconciliation scale proof failed"
trap 'code=$?; phase_finalize "$PHASE_STATUS" "$code" "$PHASE_MESSAGE"' EXIT
phase_require_file performance/settlement/run_settlement_benchmark.sh
phase_require_file scripts/phase61/verify_settlement_evidence.py
if phase_is_preflight; then
  PHASE_STATUS="PREPARED"; PHASE_MESSAGE="500k settlement, duplicate/loss/balance/outbox and reconciliation evidence verifier is ready"; exit 0
fi
phase_require_uat_confirmation
: "${SETTLEMENT_ATTESTATION:?SETTLEMENT_ATTESTATION is required}"
export SETTLEMENT_TX_COUNT=500000
export SETTLEMENT_SUMMARY_OUTPUT="$PHASE61_PHASE_DIR/settlement-500k-summary.json"
phase_run "settlement 500k benchmark" performance/settlement/run_settlement_benchmark.sh
export PERF_EXPECTED_TX_COUNT=500000
phase_run "post-settlement reconciliation" performance/scripts/reconcile-performance-data.sh "$PHASE61_PHASE_DIR/reconciliation.csv"
phase_run "financial evidence verification" python3 scripts/phase61/verify_settlement_evidence.py \
  --summary "$SETTLEMENT_SUMMARY_OUTPUT" --reconciliation "$PHASE61_PHASE_DIR/reconciliation.csv" \
  --attestation "$SETTLEMENT_ATTESTATION" --output "$PHASE61_PHASE_DIR/settlement-certification.json"
PHASE_STATUS="PASS"; PHASE_MESSAGE="500k settlement completed within SLA with zero missing/duplicate postings and balanced reconciliation"
