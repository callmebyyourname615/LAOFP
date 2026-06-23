#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE68_ROOT"; phase_setup 68F 'Execute Phase 61 UAT preflight'
if [[ ! -x scripts/phase61/run_phase61.sh ]]; then phase_finalize BLOCKED 2 'Phase 61 orchestrator missing'; exit 0; fi
if phase_preflight || phase_repo; then
  phase_log 'RUN Phase 61 repository preflight'
  set +e
  scripts/phase61/run_phase61.sh --preflight 2>&1 | tee -a "$PHASE68_LOG"
  rc=${PIPESTATUS[0]}
  set -e
  if [[ $rc -eq 0 ]]; then phase_finalize PREPARED 0 'Phase 61 preflight ready; UAT execution pending'; else phase_finalize BLOCKED "$rc" 'Phase 61 preflight is not green; resolve its reported blockers'; fi
  exit 0
fi
require_uat
phase_run 'execute Phase 61 full UAT certification' env TARGET_ENVIRONMENT=uat PHASE61_EXECUTE_RUNTIME=true CONFIRM_UAT_DRILLS=yes scripts/phase61/run_phase61.sh --full
phase_finalize PASS 0 'Phase 61A-61J executed against frozen UAT revision'
