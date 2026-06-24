#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE75_ROOT"; phase_setup 75B 'Phase 54C deployment and rollback rehearsal acceptance'
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'deployment, rollback and forward-fix acceptance gate ready'; exit 0; fi
require_uat; require_identity; : "${RELEASE_REFERENCE:?RELEASE_REFERENCE required}"
export CERTIFICATION_ENVIRONMENT=uat RELEASE_GIT_COMMIT="$PHASE75_COMMIT" RELEASE_IMAGE_DIGEST="$APPLICATION_IMAGE_DIGEST"
phase_run 'Phase 54C deployment rehearsal' scripts/certification/run_phase54_certification.sh 54C
phase_run 'verify 54C acceptance' python3 scripts/phase75/verify_phase54_acceptance.py --phases 54C --output "$PHASE75_DIR/acceptance.json" --commit "$PHASE75_COMMIT" --image-digest "$APPLICATION_IMAGE_DIGEST" --reference "$RELEASE_REFERENCE"
phase_finalize PASS 0 'deployment rehearsal and rollback accepted within SLA'
