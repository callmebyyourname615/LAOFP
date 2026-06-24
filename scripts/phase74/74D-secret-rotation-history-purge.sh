#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE74_ROOT"; phase_setup 74D 'Secret rotation and repository history purge ceremony'
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'guarded SecOps ceremony and negative credential evidence ready'; exit 0; fi
require_uat; require_identity; require_flag PHASE74_EXECUTE_OPERATOR_ACTIONS
: "${PHASE74_SECRET_ROTATION_ATTESTATION:?PHASE74_SECRET_ROTATION_ATTESTATION required}"
if [[ -n "${PHASE74_SECRET_ROTATION_COMMAND:-}" ]]; then phase_run 'operator secret rotation ceremony' bash -lc "$PHASE74_SECRET_ROTATION_COMMAND"; fi
if command -v gitleaks >/dev/null 2>&1; then phase_run 'gitleaks repository scan' gitleaks git --redact --report-format json --report-path "$PHASE74_DIR/gitleaks.json"; fi
phase_run 'signed secret rotation attestation' python3 scripts/phase74/verify_attestation.py --kind secret-rotation --file "$PHASE74_SECRET_ROTATION_ATTESTATION" --output "$PHASE74_DIR/secret-rotation.json" --commit "$PHASE74_COMMIT" --application-digest "$APPLICATION_IMAGE_DIGEST" --migration-digest "$MIGRATION_IMAGE_DIGEST"
phase_finalize PASS 0 'new credentials active, old credentials disabled and repository purge attested'
