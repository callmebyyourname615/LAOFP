#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE78_ROOT"; phase_setup 78E 'Secret rotation and repository purge ceremony'
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'operator ceremony gate ready'; exit 0; fi
require_uat; require_flag PHASE78_EXECUTE_OPERATOR_ACTIONS; require_identity; : "${PHASE78_SECRET_ROTATION_ATTESTATION:?required}"
phase_run 'secret rotation attestation' python3 scripts/phase78/verify_attestation.py --kind secret-rotation --file "$PHASE78_SECRET_ROTATION_ATTESTATION" --output "$PHASE78_DIR/secret-rotation.json" --commit "$PHASE78_COMMIT" --application-digest "$APPLICATION_IMAGE_DIGEST" --migration-digest "$MIGRATION_IMAGE_DIGEST"
phase_finalize PASS 0 'secret rotation and history purge passed'
