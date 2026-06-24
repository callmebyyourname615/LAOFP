#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE75_ROOT"; phase_setup 75C 'Phase 54D-54E capacity and settlement acceptance'
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'capacity and settlement acceptance gate ready'; exit 0; fi
require_uat; require_identity; : "${RELEASE_REFERENCE:?RELEASE_REFERENCE required}"
export CERTIFICATION_ENVIRONMENT=uat RELEASE_GIT_COMMIT="$PHASE75_COMMIT" RELEASE_IMAGE_DIGEST="$APPLICATION_IMAGE_DIGEST"
phase_run 'Phase 54D performance' scripts/certification/run_phase54_certification.sh 54D
phase_run 'Phase 54E settlement' scripts/certification/run_phase54_certification.sh 54E
phase_run 'verify 54D/54E acceptance' python3 scripts/phase75/verify_phase54_acceptance.py --phases 54D 54E --output "$PHASE75_DIR/acceptance.json" --commit "$PHASE75_COMMIT" --image-digest "$APPLICATION_IMAGE_DIGEST" --reference "$RELEASE_REFERENCE"
phase_finalize PASS 0 'capacity plan and settlement 500K evidence accepted'
