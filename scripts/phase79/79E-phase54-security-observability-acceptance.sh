#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE79_ROOT"; phase_setup 79E 'Phase 54H-54I security and observability acceptance'
[[ -x scripts/certification/run_phase54_certification.sh ]] || { phase_finalize BLOCKED 2 'Phase 54 runner missing'; exit 0; }
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'security and observability acceptance ready'; exit 0; fi
require_identity
phase_run '54H' scripts/certification/run_phase54_certification.sh 54H
phase_run '54I' scripts/certification/run_phase54_certification.sh 54I
phase_finalize PASS 0 'security and observability acceptance passed'
