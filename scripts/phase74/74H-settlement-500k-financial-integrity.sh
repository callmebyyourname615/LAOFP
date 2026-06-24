#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE74_ROOT"; phase_setup 74H 'Settlement 500K and financial integrity certification'
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'settlement 500K and accounting invariant verifier ready'; exit 0; fi
require_uat; require_identity; require_flag PHASE74_EXECUTE_LOAD
: "${PHASE74_SETTLEMENT_COMMAND:?PHASE74_SETTLEMENT_COMMAND required}"
: "${PHASE74_SETTLEMENT_RESULT:?PHASE74_SETTLEMENT_RESULT required}"
: "${PHASE74_SETTLEMENT_ATTESTATION:?PHASE74_SETTLEMENT_ATTESTATION required}"
phase_run 'settlement 500K benchmark' bash -lc "$PHASE74_SETTLEMENT_COMMAND"
phase_run 'financial invariant verification' python3 scripts/phase74/verify_financial_evidence.py --kind settlement --file "$PHASE74_SETTLEMENT_RESULT" --output "$PHASE74_DIR/settlement-verification.json"
phase_run 'settlement attestation' python3 scripts/phase74/verify_attestation.py --kind settlement --file "$PHASE74_SETTLEMENT_ATTESTATION" --output "$PHASE74_DIR/settlement-attestation.json" --commit "$PHASE74_COMMIT" --application-digest "$APPLICATION_IMAGE_DIGEST" --migration-digest "$MIGRATION_IMAGE_DIGEST"
phase_finalize PASS 0 'settlement 500K completed within SLA with balanced and idempotent postings'
