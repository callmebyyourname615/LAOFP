#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE78_ROOT"; phase_setup 78F 'SMOS runtime security certification'
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'SMOS provisioning, MFA, RBAC and maker-checker gate ready'; exit 0; fi
require_uat; require_identity; : "${PHASE78_SMOS_ATTESTATION:?required}"
phase_run 'SMOS attestation' python3 scripts/phase78/verify_attestation.py --kind smos-runtime --file "$PHASE78_SMOS_ATTESTATION" --output "$PHASE78_DIR/smos-runtime.json" --commit "$PHASE78_COMMIT" --application-digest "$APPLICATION_IMAGE_DIGEST" --migration-digest "$MIGRATION_IMAGE_DIGEST"
phase_finalize PASS 0 'SMOS runtime security passed'
