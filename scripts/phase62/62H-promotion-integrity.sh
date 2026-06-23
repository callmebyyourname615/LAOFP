#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE62_ROOT"
phase_setup 62H "Promotion budget and funder ledger integrity"
phase_run "promotion contract" python3 scripts/phase62/verify_promotion_integrity.py
if phase_is_preflight; then phase_finalize PREPARED 0 "promotion remains disabled by default; financial controls are ready"; exit 0; fi
phase_run "promotion tests" ./mvnw -B -Dtest=PromotionEligibilityEvaluatorTest,PromotionBudgetServiceIntegrationTest test
phase_finalize PASS 0 "promotion DSL and financial controls passed"
