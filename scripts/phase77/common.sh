#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"; export ROOT
export RUN_ID="${RUN_ID:-phase77-$(date -u +%Y%m%dT%H%M%SZ)}"
export GIT_COMMIT="${GIT_COMMIT:-$(git -C "$ROOT" rev-parse HEAD 2>/dev/null || echo unknown)}"
export PHASE77_EVIDENCE_DIR="${PHASE77_EVIDENCE_DIR:-$ROOT/evidence/phase77/$RUN_ID}"
mkdir -p "$PHASE77_EVIDENCE_DIR/results" "$PHASE77_EVIDENCE_DIR/logs"
write_result(){
  local args=(--phase "$1" --status "$2" --message "$3" --output "$PHASE77_EVIDENCE_DIR/results/$1.json" --synthetic)
  [[ -n "${4:-}" ]] && args+=(--details "$4")
  python3 "$ROOT/scripts/phase76/write_result.py" "${args[@]}"
}
