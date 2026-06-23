#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE68_ROOT"; phase_setup 68I 'Execute Phase 64-66 signed entry chain'
missing=()
for p in 64 65 66; do [[ -x "scripts/phase$p/run_phase$p.sh" ]] || missing+=("phase$p"); done
if ((${#missing[@]})); then phase_finalize BLOCKED 2 "authoritative orchestrators missing: ${missing[*]}"; exit 0; fi
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'Phase 64-66 orchestrators present; signed UAT entry chain pending'; exit 0; fi
require_uat
phase_run 'execute Phase 64' scripts/phase64/run_phase64.sh --full
phase_run 'execute Phase 65' scripts/phase65/run_phase65.sh --full
phase_run 'execute Phase 66' scripts/phase66/run_phase66.sh --full
phase_finalize PASS 0 'Phase 64, 65 and 66 signed entry gates passed'
