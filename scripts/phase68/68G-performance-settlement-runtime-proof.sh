#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE68_ROOT"; phase_setup 68G 'Performance and settlement runtime proof'
for f in performance/scripts/run-k6.sh performance/settlement/run_settlement_benchmark.sh; do require_file "$f"; done
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 '10K/20K/8h/500K tooling present; UAT load execution pending'; exit 0; fi
require_uat
[[ "${PHASE68_EXECUTE_LOAD:-false}" == true ]] || { phase_log 'PHASE68_EXECUTE_LOAD=true required'; exit 64; }
: "${PHASE68_PERFORMANCE_ATTESTATION:?PHASE68_PERFORMANCE_ATTESTATION is required}"
export RUN_ID="$PHASE68_RUN_ID" RESULT_DIR="$PHASE68_DIR/performance"
phase_run 'k6 smoke' performance/scripts/run-k6.sh smoke
phase_run 'k6 sustained 2K' performance/scripts/run-k6.sh sustained2k
phase_run 'k6 sustained 10K' performance/scripts/run-k6.sh sustained10k
phase_run 'k6 burst 20K' performance/scripts/run-k6.sh burst20k
phase_run 'k6 soak 8h' performance/scripts/run-k6.sh soak8h
phase_run 'settlement 500K' env SETTLEMENT_TX_COUNT=500000 RESULT_DIR="$PHASE68_DIR/settlement" performance/settlement/run_settlement_benchmark.sh
phase_run 'verify performance attestation' python3 scripts/phase68/verify_attestation.py --kind performance --file "$PHASE68_PERFORMANCE_ATTESTATION" --output "$PHASE68_DIR/performance-attestation-verification.json"
phase_finalize PASS 0 'smoke, 2K, 10K, 20K, 8h soak and settlement 500K passed'
