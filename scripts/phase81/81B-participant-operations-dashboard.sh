#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib.sh"

phase81_init 81B
for f in src/main/java/com/example/switching/dashboard/participant/controller/ParticipantDashboardController.java src/main/java/com/example/switching/dashboard/participant/service/ParticipantDashboardService.java; do [[ -f "$f" ]] || { phase81_emit BLOCKED "missing $f"; exit 1; }; done
if phase81_full; then
  [[ -n "${PHASE81_DASHBOARD_SMOKE_COMMAND:-}" ]] || { phase81_emit BLOCKED 'dashboard smoke command missing'; exit 1; }
  bash -lc "$PHASE81_DASHBOARD_SMOKE_COMMAND" > "$PHASE81_EVIDENCE_ROOT/artifacts/81B-smoke.log" 2>&1
  phase81_emit PASS 'participant operations dashboard implementation and runtime smoke passed'
else
  phase81_emit PREPARED 'participant operations dashboard implementation present; endpoint remains feature-gated'
fi
