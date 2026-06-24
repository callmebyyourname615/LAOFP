#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE75_ROOT"; phase_setup 75E 'Phase 54H security and supply-chain acceptance'
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'security, SBOM, signatures and provenance acceptance gate ready'; exit 0; fi
require_uat; require_identity; : "${RELEASE_REFERENCE:?RELEASE_REFERENCE required}"
export CERTIFICATION_ENVIRONMENT=uat RELEASE_GIT_COMMIT="$PHASE75_COMMIT" RELEASE_IMAGE_DIGEST="$APPLICATION_IMAGE_DIGEST"
phase_run 'Phase 54H security' scripts/certification/run_phase54_certification.sh 54H
phase_run 'verify 54H acceptance' python3 scripts/phase75/verify_phase54_acceptance.py --phases 54H --output "$PHASE75_DIR/phase54-acceptance.json" --commit "$PHASE75_COMMIT" --image-digest "$APPLICATION_IMAGE_DIGEST" --reference "$RELEASE_REFERENCE"
# Production-scoped attestation is checked later; this file certifies immutable supply-chain identity.
phase_finalize PASS 0 'security and supply-chain certification accepted'
