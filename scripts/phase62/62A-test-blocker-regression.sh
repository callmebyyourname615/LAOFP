#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE62_ROOT"
phase_setup 62A "Remaining test blocker regression closure"
phase_run "static regression checks" python3 scripts/phase62/verify_test_blocker_fixes.py
if phase_is_preflight; then phase_finalize PREPARED 0 "four historic blockers are statically guarded"; exit 0; fi
phase_run "targeted Maven tests" ./mvnw -B -Dtest=MigrationApplicationIntegrationTest,SanctionsScreeningIntegrationTest,OperationsGenerateRoutesForBankIntegrationTest,CrossBorderAmlBlockIntegrationTest test
phase_finalize PASS 0 "targeted blocker tests passed"
