#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib.sh"

phase81_init 81C
for f in src/main/java/com/example/switching/dashboard/infrastructure/controller/InfrastructureDashboardController.java src/main/java/com/example/switching/dashboard/infrastructure/service/InfrastructureDashboardService.java; do [[ -f "$f" ]] || { phase81_emit BLOCKED "missing $f"; exit 1; }; done
if phase81_full; then
  [[ -n "${PHASE81_DASHBOARD_SMOKE_COMMAND:-}" ]] || { phase81_emit BLOCKED 'dashboard smoke command missing'; exit 1; }
  bash -lc "$PHASE81_DASHBOARD_SMOKE_COMMAND" > "$PHASE81_EVIDENCE_ROOT/artifacts/81C-smoke.log" 2>&1
  phase81_emit PASS 'infrastructure operations dashboard implementation and runtime smoke passed'
else
  phase81_emit PREPARED 'infrastructure operations dashboard implementation present; endpoint remains feature-gated'
fi
