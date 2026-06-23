#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE65_ROOT"; phase_setup 65A 'Maven historical blocker closure'
phase_run 'source regression verifier' python3 scripts/phase65/verify_historical_test_blockers.py
if phase_preflight; then phase_finalize PREPARED 0 'source regressions closed; targeted integration execution pending'; exit 0; fi
phase_run 'targeted blocker tests' ./mvnw -B -Dtest=MigrationApplicationIntegrationTest,SanctionsScreeningIntegrationTest,OperationsGenerateRoutesForBankIntegrationTest,CrossBorderAmlBlockIntegrationTest test
phase_finalize PASS 0 'all historical blocker integration tests passed'
