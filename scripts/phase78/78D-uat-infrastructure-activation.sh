#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE78_ROOT"; phase_setup 78D 'UAT infrastructure activation'
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'UAT dependency and 24-hour stability attestation ready'; exit 0; fi
require_uat; require_identity; : "${PHASE78_UAT_INFRA_ATTESTATION:?required}"
phase_run 'UAT infrastructure attestation' python3 scripts/phase78/verify_attestation.py --kind uat-infrastructure --file "$PHASE78_UAT_INFRA_ATTESTATION" --output "$PHASE78_DIR/uat-infrastructure.json" --commit "$PHASE78_COMMIT" --application-digest "$APPLICATION_IMAGE_DIGEST" --migration-digest "$MIGRATION_IMAGE_DIGEST"
phase_finalize PASS 0 'UAT infrastructure and stability evidence passed'
