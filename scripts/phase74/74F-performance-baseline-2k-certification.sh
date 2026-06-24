#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE74_ROOT"; phase_setup 74F 'Performance baseline and sustained 2K certification'
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'smoke and sustained-2K execution policy ready'; exit 0; fi
require_uat; require_identity; require_flag PHASE74_EXECUTE_LOAD
: "${PHASE74_PERFORMANCE_BASELINE_COMMAND:?PHASE74_PERFORMANCE_BASELINE_COMMAND required}"
: "${PHASE74_PERFORMANCE_BASELINE_ATTESTATION:?PHASE74_PERFORMANCE_BASELINE_ATTESTATION required}"
phase_run 'smoke and sustained-2K scenarios' bash -lc "$PHASE74_PERFORMANCE_BASELINE_COMMAND"
phase_run 'performance baseline attestation' python3 scripts/phase74/verify_attestation.py --kind performance-baseline --file "$PHASE74_PERFORMANCE_BASELINE_ATTESTATION" --output "$PHASE74_DIR/performance-baseline.json" --commit "$PHASE74_COMMIT" --application-digest "$APPLICATION_IMAGE_DIGEST" --migration-digest "$MIGRATION_IMAGE_DIGEST"
phase_finalize PASS 0 'smoke and sustained-2K performance baseline passed'
