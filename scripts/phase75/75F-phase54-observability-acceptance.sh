#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE75_ROOT"; phase_setup 75F 'Phase 54I observability acceptance'
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'metrics, logs, traces and alert acceptance gate ready'; exit 0; fi
require_uat; require_identity; : "${RELEASE_REFERENCE:?RELEASE_REFERENCE required}"
export CERTIFICATION_ENVIRONMENT=uat RELEASE_GIT_COMMIT="$PHASE75_COMMIT" RELEASE_IMAGE_DIGEST="$APPLICATION_IMAGE_DIGEST"
phase_run 'Phase 54I observability' scripts/certification/run_phase54_certification.sh 54I
phase_run 'verify 54I acceptance' python3 scripts/phase75/verify_phase54_acceptance.py --phases 54I --output "$PHASE75_DIR/acceptance.json" --commit "$PHASE75_COMMIT" --image-digest "$APPLICATION_IMAGE_DIGEST" --reference "$RELEASE_REFERENCE"
phase_finalize PASS 0 'end-to-end telemetry, redaction and critical alert routing accepted'
