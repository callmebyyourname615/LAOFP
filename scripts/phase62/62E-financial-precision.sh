#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE62_ROOT"
phase_setup 62E "Financial numeric precision standardisation"
phase_run "precision static contract" python3 scripts/phase62/verify_financial_precision.py
if phase_is_preflight; then phase_finalize PREPARED 0 "V104 and Java precision policy are ready"; exit 0; fi
phase_run "precision tests" ./mvnw -B -Dtest=MoneyPrecisionPolicyTest,V104FinancialPrecisionMigrationIntegrationTest test
phase_finalize PASS 0 "precision policy and migration tests passed"
