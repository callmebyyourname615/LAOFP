#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE79_ROOT"; phase_setup 79C 'Phase 54D-54E capacity and settlement acceptance'
[[ -x scripts/certification/run_phase54_certification.sh ]] || { phase_finalize BLOCKED 2 'Phase 54 runner missing'; exit 0; }
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'capacity and settlement acceptance ready'; exit 0; fi
require_identity
phase_run '54D' scripts/certification/run_phase54_certification.sh 54D
phase_run '54E' scripts/certification/run_phase54_certification.sh 54E
phase_finalize PASS 0 'capacity and settlement acceptance passed'
