#!/usr/bin/env bash
set -euo pipefail

PHASE69_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PHASE69_MODE="${PHASE69_MODE:-preflight}"
PHASE69_RUN_ID="${PHASE69_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
PHASE69_EVIDENCE_ROOT="${PHASE69_EVIDENCE_ROOT:-$PHASE69_ROOT/evidence/phase69/$PHASE69_RUN_ID}"
PHASE69_RESULTS_DIR="$PHASE69_EVIDENCE_ROOT/results"
PHASE69_LOG_DIR="$PHASE69_EVIDENCE_ROOT/logs"
mkdir -p "$PHASE69_RESULTS_DIR" "$PHASE69_LOG_DIR"
export PHASE69_MODE PHASE69_RUN_ID PHASE69_EVIDENCE_ROOT PHASE69_RESULTS_DIR PHASE69_LOG_DIR

phase69_result() {
  local phase="$1" status="$2" message="$3"
  shift 3
  python3 "$PHASE69_ROOT/scripts/phase69/write_phase_result.py" \
    --phase "$phase" --status "$status" --message "$message" \
    --mode "$PHASE69_MODE" --output "$PHASE69_RESULTS_DIR/$phase.json" "$@"
}

phase69_require_full_confirmation() {
  if [[ "$PHASE69_MODE" != "full" ]]; then
    return 1
  fi
  if [[ "${PHASE69_CONFIRM_FULL:-}" != "YES" ]]; then
    phase69_result "$1" BLOCKED "Full execution requires PHASE69_CONFIRM_FULL=YES"
    echo "ERROR: full execution not confirmed" >&2
    exit 2
  fi
}

phase69_run_logged() {
  local log_name="$1"; shift
  set +e
  "$@" 2>&1 | tee "$PHASE69_LOG_DIR/$log_name.log"
  local rc=${PIPESTATUS[0]}
  set -e
  return "$rc"
}

phase69_git_sha() {
  git -C "$PHASE69_ROOT" rev-parse HEAD 2>/dev/null || printf 'unknown\n'
}
