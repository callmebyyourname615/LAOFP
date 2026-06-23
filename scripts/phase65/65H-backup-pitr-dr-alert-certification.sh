#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE65_ROOT"; phase_setup 65H 'Backup PITR DR and alert certification'
for f in scripts/phase61/61I-resilience-alert-drills.sh scripts/phase61/run_resilience_certification.sh scripts/phase61/verify_resilience_evidence.py; do [[ -f "$f" ]] || { phase_finalize BLOCKED 1 "missing $f"; exit 1; }; done
if phase_preflight; then phase_finalize PREPARED 0 'backup/PITR/failover/alert lifecycle tooling ready'; exit 0; fi
require_uat
export PHASE61_RUN_ID="${PHASE65_RUN_ID}-resilience" PHASE61_EVIDENCE_ROOT="$PHASE65_DIR/phase61-resilience" PHASE61_EXECUTE_RUNTIME=true CONFIRM_UAT_DRILLS=yes
phase_run 'Phase 61I resilience and alert certification' scripts/phase61/61I-resilience-alert-drills.sh
phase_run 'verify resilience result' python3 scripts/phase65/verify_nested_results.py --root "$PHASE61_EVIDENCE_ROOT" --prefix 61 --expected 1 --output "$PHASE65_DIR/resilience-result.json"
phase_finalize PASS 0 'backup, PITR, failover, recovery and critical alert lifecycle certified'
