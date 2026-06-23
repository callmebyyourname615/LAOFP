#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE68_ROOT"; phase_setup 68A 'Remaining P0 test fixes and authorization regression'
phase_run 'verify source regressions' python3 scripts/phase68/verify_p0_test_fixes.py --output "$PHASE68_DIR/source-regression.json"
phase_run 'audit explicit endpoint authorization' python3 scripts/phase68/audit_admin_authorization.py --output "$PHASE68_DIR/authorization-audit.json"
if phase_preflight; then phase_finalize PREPARED 0 'source fixes verified; targeted Maven integration tests pending'; exit 0; fi
phase_run 'targeted blocker tests' ./mvnw -B -Dtest=MigrationApplicationIntegrationTest,SanctionsScreeningIntegrationTest,OperationsGenerateRoutesForBankIntegrationTest,CrossBorderAmlBlockIntegrationTest test
phase_finalize PASS 0 'historical P0 blocker tests and authorization audit passed'
