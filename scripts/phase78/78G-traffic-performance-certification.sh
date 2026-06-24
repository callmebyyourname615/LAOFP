#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE78_ROOT"; phase_setup 78G 'Traffic and performance certification'
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'smoke, 2K, 10K, 20K and soak gate ready'; exit 0; fi
require_uat; require_flag PHASE78_EXECUTE_LOAD; require_identity; : "${PHASE78_PERFORMANCE_ATTESTATION:?required}"
[[ -n "${PHASE78_PERFORMANCE_COMMAND:-}" ]] && phase_run 'performance suite' bash -lc "$PHASE78_PERFORMANCE_COMMAND"
phase_run 'performance attestation' python3 scripts/phase78/verify_attestation.py --kind performance --file "$PHASE78_PERFORMANCE_ATTESTATION" --output "$PHASE78_DIR/performance.json" --commit "$PHASE78_COMMIT" --application-digest "$APPLICATION_IMAGE_DIGEST" --migration-digest "$MIGRATION_IMAGE_DIGEST"
phase_finalize PASS 0 'traffic and performance evidence passed'
