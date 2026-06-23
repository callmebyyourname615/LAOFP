#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE65_ROOT"; phase_setup 65F 'Execute Phase 61 UAT preflight'
[[ -x scripts/phase61/run_phase61.sh ]] || { phase_finalize BLOCKED 1 'Phase 61 orchestrator missing'; exit 1; }
if phase_preflight; then phase_finalize PREPARED 0 'Phase 61 full-execution wrapper ready'; exit 0; fi
require_uat
export PHASE61_RUN_ID="$PHASE65_RUN_ID" PHASE61_EVIDENCE_ROOT="$PHASE65_DIR/phase61-evidence" PHASE61_EXECUTE_RUNTIME=true CONFIRM_UAT_DRILLS=yes
phase_run 'Phase 61 full certification' scripts/phase61/run_phase61.sh --full
phase_run 'verify all Phase 61 results' python3 scripts/phase65/verify_nested_results.py --root "$PHASE61_EVIDENCE_ROOT" --prefix 61 --expected 10 --output "$PHASE65_DIR/phase61-certification.json"
phase_finalize PASS 0 'Phase 61A–61J passed against UAT'
