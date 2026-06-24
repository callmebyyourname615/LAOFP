#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib.sh"

phase81_init 81D
for f in src/main/java/com/example/switching/dashboard/dr/controller/DrDashboardController.java src/main/java/com/example/switching/dashboard/dr/service/DrDashboardService.java; do [[ -f "$f" ]] || { phase81_emit BLOCKED "missing $f"; exit 1; }; done
if phase81_full; then
  [[ -n "${PHASE81_DASHBOARD_SMOKE_COMMAND:-}" ]] || { phase81_emit BLOCKED 'dashboard smoke command missing'; exit 1; }
  bash -lc "$PHASE81_DASHBOARD_SMOKE_COMMAND" > "$PHASE81_EVIDENCE_ROOT/artifacts/81D-smoke.log" 2>&1
  phase81_emit PASS 'DR readiness dashboard implementation and runtime smoke passed'
else
  phase81_emit PREPARED 'DR readiness dashboard implementation present; endpoint remains feature-gated'
fi
