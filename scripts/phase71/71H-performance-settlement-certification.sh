#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE71_ROOT"; phase_setup 71H 'Performance, rate-limit and settlement 500K certification'
for f in performance/scripts/run-k6.sh performance/settlement/run_settlement_benchmark.sh config/phase71-performance-policy.yaml; do require_file "$f"; done
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'load and financial integrity tooling ready; UAT execution pending'; exit 0; fi
require_uat; require_flag PHASE71_EXECUTE_LOAD
: "${PHASE71_PERFORMANCE_ATTESTATION:?PHASE71_PERFORMANCE_ATTESTATION is required}"
export RUN_ID="$PHASE71_RUN_ID" RESULT_DIR="$PHASE71_DIR/performance"
phase_run 'k6 smoke' performance/scripts/run-k6.sh smoke
phase_run 'k6 sustained 2K' performance/scripts/run-k6.sh sustained2k
phase_run 'k6 sustained 10K' performance/scripts/run-k6.sh sustained10k
phase_run 'k6 burst 20K' performance/scripts/run-k6.sh burst20k
phase_run 'k6 soak 8h' performance/scripts/run-k6.sh soak8h
phase_run 'settlement 500K' env SETTLEMENT_TX_COUNT=500000 RESULT_DIR="$PHASE71_DIR/settlement" performance/settlement/run_settlement_benchmark.sh
phase_run 'verify performance attestation' python3 scripts/phase71/verify_attestation.py --kind performance-settlement --file "$PHASE71_PERFORMANCE_ATTESTATION" --output "$PHASE71_DIR/performance-attestation.json"
phase_finalize PASS 0 '10K, 20K, soak, rate-limit tuning and settlement 500K certified'
