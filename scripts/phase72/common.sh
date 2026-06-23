#!/usr/bin/env bash
set -euo pipefail

PHASE72_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PHASE72_MODE="${PHASE72_MODE:-preflight}"
PHASE72_RUN_ID="${PHASE72_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
PHASE72_EVIDENCE_ROOT="${PHASE72_EVIDENCE_ROOT:-$PHASE72_ROOT/evidence/phase72/$PHASE72_RUN_ID}"
PHASE72_RESULTS_DIR="$PHASE72_EVIDENCE_ROOT/results"
PHASE72_LOG_DIR="$PHASE72_EVIDENCE_ROOT/logs"
PHASE72_ARTIFACT_DIR="$PHASE72_EVIDENCE_ROOT/artifacts"
mkdir -p "$PHASE72_RESULTS_DIR" "$PHASE72_LOG_DIR" "$PHASE72_ARTIFACT_DIR"
export PHASE72_ROOT PHASE72_MODE PHASE72_RUN_ID PHASE72_EVIDENCE_ROOT PHASE72_RESULTS_DIR PHASE72_LOG_DIR PHASE72_ARTIFACT_DIR

phase72_git_sha() {
  git -C "$PHASE72_ROOT" rev-parse HEAD 2>/dev/null || printf 'unknown
'
}

phase72_result() {
  local phase="$1" status="$2" message="$3"; shift 3
  python3 "$PHASE72_ROOT/scripts/phase72/write_phase_result.py"     --phase "$phase" --status "$status" --message "$message"     --mode "$PHASE72_MODE" --git-commit "${PHASE72_GIT_SHA:-$(phase72_git_sha)}"     --output "$PHASE72_RESULTS_DIR/$phase.json" "$@"
}

phase72_run_logged() {
  local name="$1"; shift
  set +e
  "$@" 2>&1 | tee "$PHASE72_LOG_DIR/$name.log"
  local rc=${PIPESTATUS[0]}
  set -e
  return "$rc"
}

phase72_require_full() {
  local phase="$1" confirmation_var="${2:-PHASE72_CONFIRM_FULL}"
  if [[ "$PHASE72_MODE" != "full" ]]; then
    return 1
  fi
  if [[ "${!confirmation_var:-}" != "YES" ]]; then
    phase72_result "$phase" BLOCKED "Full execution requires $confirmation_var=YES"
    echo "ERROR: $confirmation_var is not confirmed" >&2
    exit 2
  fi
  return 0
}

phase72_has_phase71() {
  [[ -d "$PHASE72_ROOT/scripts/phase71" || -d "$PHASE72_ROOT/docs/phase71" ]]
}

phase72_command_exists() { command -v "$1" >/dev/null 2>&1; }
