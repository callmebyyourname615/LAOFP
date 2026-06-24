#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE79_ROOT"; phase_setup 79F 'Phase 54J immutable release candidate freeze'
[[ -x scripts/certification/run_phase54_certification.sh ]] || { phase_finalize BLOCKED 2 'Phase 54 runner missing'; exit 0; }
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'immutable release candidate freeze ready'; exit 0; fi
require_identity
phase_run '54J' scripts/certification/run_phase54_certification.sh 54J
phase_finalize PASS 0 'immutable release candidate freeze passed'
