#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE79_ROOT"; phase_setup 79B 'Phase 54C deployment rehearsal'
[[ -x scripts/certification/run_phase54_certification.sh ]] || { phase_finalize BLOCKED 2 'Phase 54 runner missing'; exit 0; }
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'deployment rehearsal acceptance ready'; exit 0; fi
require_identity
phase_run '54C' scripts/certification/run_phase54_certification.sh 54C
phase_finalize PASS 0 'deployment rehearsal acceptance passed'
