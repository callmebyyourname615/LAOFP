#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE79_ROOT"; phase_setup 79D 'Phase 54F-54G resilience acceptance'
[[ -x scripts/certification/run_phase54_certification.sh ]] || { phase_finalize BLOCKED 2 'Phase 54 runner missing'; exit 0; }
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'backup and resilience acceptance ready'; exit 0; fi
require_identity
phase_run '54F' scripts/certification/run_phase54_certification.sh 54F
phase_run '54G' scripts/certification/run_phase54_certification.sh 54G
phase_finalize PASS 0 'backup and resilience acceptance passed'
