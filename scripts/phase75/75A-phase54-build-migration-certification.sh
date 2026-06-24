#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE75_ROOT"; phase_setup 75A 'Phase 54A-54B build and migration acceptance'
[[ -f scripts/certification/run_phase54_certification.sh ]] || { phase_finalize BLOCKED 2 'Phase 54 certification runner missing'; exit 0; }
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'Phase 54A/54B acceptance wrapper ready'; exit 0; fi
require_uat; require_identity
: "${RELEASE_REFERENCE:?RELEASE_REFERENCE required}"
export CERTIFICATION_ENVIRONMENT=uat RELEASE_GIT_COMMIT="$PHASE75_COMMIT" RELEASE_IMAGE_DIGEST="$APPLICATION_IMAGE_DIGEST"
phase_run 'Phase 54A build/test' scripts/certification/run_phase54_certification.sh 54A
phase_run 'Phase 54B migration' scripts/certification/run_phase54_certification.sh 54B
phase_run 'verify 54A/54B acceptance' python3 scripts/phase75/verify_phase54_acceptance.py --phases 54A 54B --output "$PHASE75_DIR/acceptance.json" --commit "$PHASE75_COMMIT" --image-digest "$APPLICATION_IMAGE_DIGEST" --reference "$RELEASE_REFERENCE"
phase_finalize PASS 0 'Phase 54 build and migration certifications accepted'
