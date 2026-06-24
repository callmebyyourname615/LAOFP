#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib.sh"

phase81_init 81E
python3 scripts/phase81/verify_dashboard_contract.py > "$PHASE81_EVIDENCE_ROOT/artifacts/dashboard-contract.json"
if phase81_full; then
  [[ -n "${PHASE81_PORTAL_SMOKE_COMMAND:-}" ]] || { phase81_emit BLOCKED 'portal smoke command missing'; exit 1; }
  bash -lc "$PHASE81_PORTAL_SMOKE_COMMAND" > "$PHASE81_EVIDENCE_ROOT/artifacts/portal-smoke.log" 2>&1
  phase81_emit PASS 'SMOS portal integration and RBAC/no-store contract verified'
else phase81_emit PREPARED 'portal API contract verified; UI smoke not run'; fi
