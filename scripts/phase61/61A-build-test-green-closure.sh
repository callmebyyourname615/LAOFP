#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE61_ROOT"
phase_setup "61A" "Build and test green closure"
PHASE_STATUS="FAIL"; PHASE_MESSAGE="build/test certification failed"
trap 'code=$?; phase_finalize "$PHASE_STATUS" "$code" "$PHASE_MESSAGE"' EXIT
phase_run "Phase 61 static contract" python3 scripts/verify_phase61_static.py
if phase_is_preflight; then
  PHASE_STATUS="PREPARED"; PHASE_MESSAGE="build, report freshness and flaky-test certification contracts are ready"; exit 0
fi
phase_require_command java
phase_run "clean Maven verification" ./mvnw -B -Dbuild.commit="$(git rev-parse HEAD)" clean verify
phase_run "current test report certification" python3 scripts/phase61/certify_test_reports.py \
  --root . --minimum-tests 451 --output "$PHASE61_PHASE_DIR/test-certification.json"
if [[ "${PHASE61_REPEAT_CRITICAL_TESTS:-true}" == "true" ]]; then
  critical="V101SmosSecurityHardeningMigrationIntegrationTest,SmosSecurityCertificationIntegrationTest,CriticalDashboardDataAcceptanceIntegrationTest,V100CurrentStatusReportingRepairIntegrationTest,SettlementLifecycleIntegrationTest"
  for iteration in 1 2 3; do
    phase_run "critical tests stability run $iteration" ./mvnw -B -Dtest="$critical" test
  done
fi
PHASE_STATUS="PASS"; PHASE_MESSAGE="Maven verify passed with fresh reports, zero failures/errors and stable critical tests"
