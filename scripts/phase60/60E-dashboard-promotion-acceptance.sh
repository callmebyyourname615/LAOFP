#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE60_ROOT"
phase_setup "60E" "Dashboard and promotion acceptance"
PHASE_STATUS="FAIL"
PHASE_MESSAGE="dashboard or promotion acceptance failed"
trap 'code=$?; phase_finalize "$PHASE_STATUS" "$code" "$PHASE_MESSAGE"' EXIT

phase_run "critical dashboard static contract" python3 scripts/verify_critical_dashboards_static.py

if phase_is_preflight; then
  PHASE_STATUS="PREPARED"
  PHASE_MESSAGE="dashboard and safe promotion DSL acceptance tests are present; execution requires Maven/Testcontainers"
  exit 0
fi

phase_run "dashboard and promotion acceptance tests" ./mvnw -B \
  -Dtest=CriticalDashboardIntegrationTest,CriticalDashboardDataAcceptanceIntegrationTest,PromotionEligibilityEvaluatorTest \
  test

PHASE_STATUS="PASS"
PHASE_MESSAGE="critical dashboard data contracts and bounded promotion eligibility DSL passed acceptance tests"
