#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE65_ROOT"; phase_setup 65G 'Performance and settlement certification'
for f in scripts/phase61/61G-performance-capacity-certification.sh scripts/phase61/61H-settlement-reconciliation-scale.sh scripts/phase61/verify_capacity_attestation.py scripts/phase61/verify_settlement_evidence.py; do [[ -f "$f" ]] || { phase_finalize BLOCKED 1 "missing $f"; exit 1; }; done
if phase_preflight; then phase_finalize PREPARED 0 '10K/20K/8h and settlement-500K certification tooling ready'; exit 0; fi
require_uat
export PHASE61_RUN_ID="${PHASE65_RUN_ID}-performance" PHASE61_EVIDENCE_ROOT="$PHASE65_DIR/phase61-performance" PHASE61_EXECUTE_RUNTIME=true CONFIRM_UAT_DRILLS=yes
phase_run 'Phase 61G performance certification' scripts/phase61/61G-performance-capacity-certification.sh
phase_run 'Phase 61H settlement certification' scripts/phase61/61H-settlement-reconciliation-scale.sh
phase_run 'verify performance and settlement results' python3 scripts/phase65/verify_nested_results.py --root "$PHASE61_EVIDENCE_ROOT" --prefix 61 --expected 2 --output "$PHASE65_DIR/performance-settlement-results.json"
phase_finalize PASS 0 '10K sustained, 20K burst, 8h soak and settlement 500K certified'
