#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib.sh"

phase81_init 81G
if phase81_full; then
  [[ -f "${PHASE81_TRAFFIC_BASELINE:-}" ]] || { phase81_emit BLOCKED 'participant traffic baseline missing'; exit 1; }
  python3 scripts/phase81/calibrate_quotas.py "$PHASE81_TRAFFIC_BASELINE" "$PHASE81_EVIDENCE_ROOT/artifacts/quota-recommendations.json"
  phase81_emit PASS 'participant quota recommendations generated; changes require maker-checker'
else phase81_emit PREPARED 'quota calibration algorithm ready; no production quotas changed'; fi
