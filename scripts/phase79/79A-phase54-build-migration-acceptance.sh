#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE79_ROOT"; phase_setup 79A 'Phase 54A-54B formal acceptance'
[[ -x scripts/certification/run_phase54_certification.sh ]] || { phase_finalize BLOCKED 2 'Phase 54 runner missing'; exit 0; }
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'build and migration formal acceptance ready'; exit 0; fi
require_identity
phase_run '54A' scripts/certification/run_phase54_certification.sh 54A
phase_run '54B' scripts/certification/run_phase54_certification.sh 54B
phase_finalize PASS 0 'build and migration formal acceptance passed'
