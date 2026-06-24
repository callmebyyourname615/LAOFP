#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
export ROOT
export RUN_ID="${RUN_ID:-phase76-$(date -u +%Y%m%dT%H%M%SZ)}"
export GIT_COMMIT="${GIT_COMMIT:-$(git -C "$ROOT" rev-parse HEAD 2>/dev/null || echo unknown)}"
export PHASE76_EVIDENCE_DIR="${PHASE76_EVIDENCE_DIR:-$ROOT/evidence/phase76/$RUN_ID}"
mkdir -p "$PHASE76_EVIDENCE_DIR/results" "$PHASE76_EVIDENCE_DIR/logs"
write_result(){
  local args=(--phase "$1" --status "$2" --message "$3" --output "$PHASE76_EVIDENCE_DIR/results/$1.json")
  [[ -n "${4:-}" ]] && args+=(--details "$4")
  [[ "${PHASE76_MODE:-preflight}" != full ]] && args+=(--synthetic)
  python3 "$ROOT/scripts/phase76/write_result.py" "${args[@]}"
}
