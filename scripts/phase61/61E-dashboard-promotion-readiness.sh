#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE61_ROOT"
phase_setup "61E" "Operations dashboards and promotion readiness"
PHASE_STATUS="FAIL"; PHASE_MESSAGE="dashboard/promotion acceptance failed"
trap 'code=$?; phase_finalize "$PHASE_STATUS" "$code" "$PHASE_MESSAGE"' EXIT
phase_run "dashboard static contract" python3 scripts/verify_critical_dashboards_static.py
phase_run "dashboard/promotion readiness contract" python3 scripts/phase61/verify_dashboard_promotion_readiness.py
if phase_is_preflight; then
  PHASE_STATUS="PREPARED"; PHASE_MESSAGE="dashboard freshness/pagination/read-replica and promotion feature-decision contracts are ready"; exit 0
fi
phase_run "dashboard data and RBAC acceptance" ./mvnw -B \
  -Dtest=CriticalDashboardIntegrationTest,CriticalDashboardDataAcceptanceIntegrationTest test
phase_run "bounded promotion DSL acceptance" ./mvnw -B -Dtest=PromotionEligibilityEvaluatorTest test
PHASE_STATUS="PASS"; PHASE_MESSAGE="three critical dashboards and bounded promotion policy passed acceptance tests"
