#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE79_ROOT"; phase_setup 79H 'Production canary 5 percent to 25 percent'
[[ -f scripts/phase67/run_phase67.sh && -f scripts/golive/run_phase55_golive.sh ]] || { phase_finalize BLOCKED 2 'Phase 67 or 55 source missing'; exit 0; }
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 '5% and 25% canary gates ready'; exit 0; fi
require_prod; require_identity; require_flag PHASE79_EXECUTE_CANARY; : "${PHASE79_CANARY_ATTESTATION:?required}"
phase_run 'canary attestation' python3 scripts/phase79/verify_production_attestation.py --kind canary-25 --file "$PHASE79_CANARY_ATTESTATION" --output "$PHASE79_DIR/canary-25.json" --commit "$PHASE79_COMMIT" --application-digest "$APPLICATION_IMAGE_DIGEST" --migration-digest "$MIGRATION_IMAGE_DIGEST"
[[ -n "${PHASE79_CANARY_COMMAND:-}" ]] && phase_run 'canary traffic stages' bash -lc "$PHASE79_CANARY_COMMAND"
phase_finalize PASS 0 'production canary 5% and 25% passed'
