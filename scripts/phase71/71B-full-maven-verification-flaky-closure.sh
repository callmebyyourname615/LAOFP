#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE71_ROOT"; phase_setup 71B 'Full Maven verification and flaky-test closure'
if phase_preflight; then phase_finalize PREPARED 0 'fresh-report verifier and Maven gate ready'; exit 0; fi
phase_run 'clean Maven verification' ./mvnw -B clean verify
phase_run 'verify fresh test reports' python3 scripts/phase71/verify_test_reports.py --output "$PHASE71_DIR/test-summary.json"
phase_run 'run production readiness gates' ./scripts/execute-and-verify/00-run-all.sh
if [[ "${PHASE71_REPEAT_CRITICAL_TESTS:-true}" == true ]]; then
  for run in 1 2 3; do
    phase_run "critical integration repeat $run" ./mvnw -B -Dtest=CrossBorderAmlBlockIntegrationTest,SanctionsScreeningIntegrationTest,OperationsGenerateRoutesForBankIntegrationTest,SmosSecurityCertificationIntegrationTest,PromotionBudgetServiceIntegrationTest test
  done
fi
phase_finalize PASS 0 'Maven verification, readiness gates and critical repeat runs passed'
