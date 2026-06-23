#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE71_ROOT"; phase_setup 71G 'Unified UAT preflight execution'
required=(scripts/phase61/run_phase61.sh scripts/phase65/run_phase65.sh scripts/phase66/run_phase66.sh scripts/phase68/run_phase68.sh scripts/phase69/run_phase69.sh scripts/phase70/run_phase70.sh)
missing=(); for f in "${required[@]}"; do [[ -f "$f" ]] || missing+=("$f"); done
if ((${#missing[@]})); then printf '%s\n' "${missing[@]}" > "$PHASE71_DIR/missing-dependencies.txt"; phase_finalize BLOCKED 2 'authoritative prerequisite phase source is missing'; exit 0; fi
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'all prerequisite phase runners present; unified UAT execution pending'; exit 0; fi
require_uat
phase_run 'execute Phase 61' scripts/phase61/run_phase61.sh --full
phase_run 'execute Phase 65' scripts/phase65/run_phase65.sh --full
phase_run 'execute Phase 66' scripts/phase66/run_phase66.sh --full
phase_run 'execute Phase 68' scripts/phase68/run_phase68.sh --full
phase_run 'execute Phase 69' scripts/phase69/run_phase69.sh --full
phase_run 'execute Phase 70' scripts/phase70/run_phase70.sh --full
phase_finalize PASS 0 'Phase 61,65,66,68,69,70 UAT preflight chain passed'
