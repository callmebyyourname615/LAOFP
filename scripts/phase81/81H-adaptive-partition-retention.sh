#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib.sh"

phase81_init 81H
if phase81_full; then
  [[ -f "${PHASE81_PERFORMANCE_DECISION:-}" ]] || { phase81_emit BLOCKED 'performance decision missing'; exit 1; }
  python3 scripts/phase81/evaluate_partition_trigger.py "$PHASE81_PERFORMANCE_DECISION" "$PHASE81_EVIDENCE_ROOT/artifacts/partition-decision.json"
  phase81_emit PASS 'partition trigger evaluated; no migration generated automatically'
else phase81_emit PREPARED 'adaptive partition trigger ready; migration intentionally deferred'; fi
