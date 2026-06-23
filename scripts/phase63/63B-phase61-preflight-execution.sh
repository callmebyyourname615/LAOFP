#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
phase63_load_env
cd "$PHASE63_ROOT"
phase63_setup 63B 'Phase 61 compatibility and repository certification execution'
STATUS=FAIL; MESSAGE='Phase 61 compatibility/repository execution failed'
trap 'code=$?; phase63_finalize "$STATUS" "$code" "$MESSAGE"' EXIT
phase63_require_file scripts/phase63/verify_phase61_compatibility.py
phase63_require_file scripts/phase63/phase61A-current-build-test-closure.sh
phase63_require_file scripts/phase63/phase61B-current-migration-certification.sh
phase63_run 'Phase 61 compatibility against merged migration baseline' python3 scripts/phase63/verify_phase61_compatibility.py \
  --root . --output "$PHASE63_PHASE_DIR/phase61-compatibility.json"
if phase63_is_preflight; then
  STATUS=PREPARED; MESSAGE='Phase 61 tooling is compatible with the current 96-migration baseline; repository tests were not executed'; exit 0
fi
phase61_root="$PHASE63_PHASE_DIR/phase61-evidence"
export PHASE61_RUN_ID="${PHASE63_RUN_ID}-63B"
export PHASE61_EVIDENCE_ROOT="$phase61_root"
export PHASE61_PREFLIGHT_ONLY=false
phase63_run 'Phase 61A merged-baseline build/test closure' scripts/phase63/phase61A-current-build-test-closure.sh
phase63_run 'Phase 61B merged migration certification' scripts/phase63/phase61B-current-migration-certification.sh
phase63_run 'Phase 61D SMOS security certification' scripts/phase61/61D-smos-security-hardening.sh
phase63_run 'Phase 61E dashboard/promotion certification' scripts/phase61/61E-dashboard-promotion-readiness.sh
phase63_run 'verify Phase 61 repository results' python3 scripts/phase63/verify_phase61_execution.py \
  --run-dir "$phase61_root/$PHASE61_RUN_ID" --output "$PHASE63_PHASE_DIR/phase61-repository-verification.json"
STATUS=PASS; MESSAGE='Phase 61A/61B/61D/61E repository certification passed on the merged baseline'
