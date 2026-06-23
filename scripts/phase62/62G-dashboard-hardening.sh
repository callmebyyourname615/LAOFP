#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE62_ROOT"
phase_setup 62G "Critical dashboard RBAC and query hardening"
phase_run "dashboard contract" python3 scripts/phase62/verify_dashboard_hardening.py
if phase_is_preflight; then phase_finalize PREPARED 0 "dashboard hardening contract is ready"; exit 0; fi
phase_run "dashboard tests" ./mvnw -B -Dtest=CriticalDashboardIntegrationTest,CriticalDashboardDataAcceptanceIntegrationTest,DashboardAccessScopeTest test
phase_finalize PASS 0 "dashboard acceptance passed"
