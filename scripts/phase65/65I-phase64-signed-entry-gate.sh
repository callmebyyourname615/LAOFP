#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE65_ROOT"; phase_setup 65I 'Execute Phase 64 signed entry gate'
if [[ ! -x scripts/phase64/run_phase64.sh ]]; then
  phase_finalize BLOCKED 0 'Phase 64 source package is not present in this baseline; merge authoritative Phase 64 before execution'
  phase_preflight && exit 0 || exit 1
fi
if phase_preflight; then phase_finalize PREPARED 0 'Phase 64 signed gate dependency is present'; exit 0; fi
require_uat
export PHASE64_RUN_ID="$PHASE65_RUN_ID" PHASE64_EVIDENCE_ROOT="$PHASE65_DIR/phase64-evidence" PHASE64_EXECUTE_RUNTIME=true
phase_run 'Phase 64 full signed entry gate' scripts/phase64/run_phase64.sh --full
phase_run 'verify all Phase 64 results' python3 scripts/phase65/verify_nested_results.py --root "$PHASE64_EVIDENCE_ROOT" --prefix 64 --expected 10 --output "$PHASE65_DIR/phase64-certification.json"
phase_finalize PASS 0 'Phase 64 signed handoff bundle and Phase 54 entry decision passed'
