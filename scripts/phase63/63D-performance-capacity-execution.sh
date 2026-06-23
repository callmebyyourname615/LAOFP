#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
phase63_load_env
cd "$PHASE63_ROOT"
phase63_setup 63D 'Performance and capacity runtime execution'
STATUS=FAIL; MESSAGE='performance/capacity execution failed'
trap 'code=$?; phase63_finalize "$STATUS" "$code" "$MESSAGE"' EXIT
for file in performance/scenarios/{smoke,sustained-2k-tps,sustained-10k-tps,burst-20k-tps,soak-8h}.js; do phase63_require_file "$file"; done
phase63_require_file scripts/phase61/61G-performance-capacity-certification.sh
phase63_require_file scripts/phase61/verify_capacity_attestation.py
if ! phase63_is_full; then
  export PHASE61_RUN_ID="${PHASE63_RUN_ID}-63D-preflight"
  export PHASE61_EVIDENCE_ROOT="$PHASE63_PHASE_DIR/phase61-preflight"
  export PHASE61_PREFLIGHT_ONLY=true
  phase63_run 'Phase 61G performance contract' scripts/phase61/61G-performance-capacity-certification.sh
  STATUS=PREPARED; MESSAGE='smoke, 2K, 10K, 20K and 8h capacity execution is ready'; exit 0
fi
phase63_require_uat_confirmation
: "${BASE_URL:?BASE_URL is required}"
: "${API_KEY:?API_KEY is required}"
: "${PHASE63_CAPACITY_ATTESTATION:?PHASE63_CAPACITY_ATTESTATION is required}"
phase63_require_attestation "$PHASE63_CAPACITY_ATTESTATION"
export PHASE61_RUN_ID="${PHASE63_RUN_ID}-63D"
export PHASE61_EVIDENCE_ROOT="$PHASE63_PHASE_DIR/phase61-runtime"
export PHASE61_PREFLIGHT_ONLY=false
export PHASE61_EXECUTE_RUNTIME=true
export CAPACITY_ATTESTATION="$PHASE63_CAPACITY_ATTESTATION"
phase63_run 'Phase 61G full UAT performance certification' scripts/phase61/61G-performance-capacity-certification.sh
cp "$PHASE61_EVIDENCE_ROOT/$PHASE61_RUN_ID/61G/result.json" "$PHASE63_PHASE_DIR/phase61-61G-result.json"
STATUS=PASS; MESSAGE='smoke, 2K, 10K, 20K burst and 8h soak met signed capacity thresholds'
