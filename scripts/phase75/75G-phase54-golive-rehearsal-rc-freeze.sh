#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE75_ROOT"; phase_setup 75G 'Phase 54J Go-Live rehearsal and immutable RC freeze'
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 '54J rehearsal and immutable release-candidate freeze gate ready'; exit 0; fi
require_uat; require_identity; : "${RELEASE_REFERENCE:?RELEASE_REFERENCE required}"
export CERTIFICATION_ENVIRONMENT=uat RELEASE_GIT_COMMIT="$PHASE75_COMMIT" RELEASE_IMAGE_DIGEST="$APPLICATION_IMAGE_DIGEST"
phase_run 'Phase 54J rehearsal and RC' scripts/certification/run_phase54_certification.sh 54J
phase_run 'verify complete Phase 54 acceptance' python3 scripts/phase75/verify_phase54_acceptance.py --phases 54A 54B 54C 54D 54E 54F 54G 54H 54I 54J --output "$PHASE75_DIR/acceptance.json" --commit "$PHASE75_COMMIT" --image-digest "$APPLICATION_IMAGE_DIGEST" --reference "$RELEASE_REFERENCE"
phase_finalize PASS 0 'Phase 54 manifest signed and immutable release candidate frozen'
