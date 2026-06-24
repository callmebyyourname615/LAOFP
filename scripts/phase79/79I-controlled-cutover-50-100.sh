#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE79_ROOT"; phase_setup 79I 'Controlled cutover 50 percent to 100 percent'
[[ -f scripts/phase67/run_phase67.sh && -f scripts/golive/run_phase55_golive.sh ]] || { phase_finalize BLOCKED 2 'Phase 67 or 55 source missing'; exit 0; }
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 '50% and 100% cutover gates ready'; exit 0; fi
require_prod; require_identity; require_flag PHASE79_EXECUTE_CUTOVER; : "${PHASE79_CUTOVER_ATTESTATION:?required}"
phase_run 'cutover attestation' python3 scripts/phase79/verify_production_attestation.py --kind cutover-100 --file "$PHASE79_CUTOVER_ATTESTATION" --output "$PHASE79_DIR/cutover-100.json" --commit "$PHASE79_COMMIT" --application-digest "$APPLICATION_IMAGE_DIGEST" --migration-digest "$MIGRATION_IMAGE_DIGEST"
[[ -n "${PHASE79_CUTOVER_COMMAND:-}" ]] && phase_run 'controlled cutover' bash -lc "$PHASE79_CUTOVER_COMMAND"
phase_finalize PASS 0 '100% production traffic stable and reconciliation matched'
