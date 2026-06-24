#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE78_ROOT"; phase_setup 78H 'Settlement 500K and financial integrity'
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'settlement and reconciliation gate ready'; exit 0; fi
require_uat; require_flag PHASE78_EXECUTE_LOAD; require_identity; : "${PHASE78_SETTLEMENT_ATTESTATION:?required}"
[[ -n "${PHASE78_SETTLEMENT_COMMAND:-}" ]] && phase_run 'settlement 500K' bash -lc "$PHASE78_SETTLEMENT_COMMAND"
phase_run 'settlement attestation' python3 scripts/phase78/verify_attestation.py --kind settlement-500k --file "$PHASE78_SETTLEMENT_ATTESTATION" --output "$PHASE78_DIR/settlement.json" --commit "$PHASE78_COMMIT" --application-digest "$APPLICATION_IMAGE_DIGEST" --migration-digest "$MIGRATION_IMAGE_DIGEST"
phase_finalize PASS 0 'settlement and financial invariants passed'
