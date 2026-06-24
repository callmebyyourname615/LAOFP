#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE74_ROOT"; phase_setup 74E 'SMOS runtime provisioning and security certification'
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'SMOS provisioning, MFA/RBAC and maker-checker evidence gate ready'; exit 0; fi
require_uat; require_identity
: "${PHASE74_SMOS_RUNTIME_ATTESTATION:?PHASE74_SMOS_RUNTIME_ATTESTATION required}"
if [[ -n "${PHASE74_SMOS_CERTIFICATION_COMMAND:-}" ]]; then phase_run 'SMOS runtime test suite' bash -lc "$PHASE74_SMOS_CERTIFICATION_COMMAND"; fi
phase_run 'SMOS signed attestation' python3 scripts/phase74/verify_attestation.py --kind smos-runtime --file "$PHASE74_SMOS_RUNTIME_ATTESTATION" --output "$PHASE74_DIR/smos-runtime.json" --commit "$PHASE74_COMMIT" --application-digest "$APPLICATION_IMAGE_DIGEST" --migration-digest "$MIGRATION_IMAGE_DIGEST"
phase_finalize PASS 0 'operators, TOTP, RBAC, participant isolation and maker-checker certified'
