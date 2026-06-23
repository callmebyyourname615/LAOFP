#!/usr/bin/env bash
set -Eeuo pipefail

PHASE60_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export PYTHONDONTWRITEBYTECODE=1
PHASE60_ROOT="$(cd "$PHASE60_SCRIPT_DIR/../.." && pwd)"
PHASE60_RUN_ID="${PHASE60_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
PHASE60_EVIDENCE_ROOT="${PHASE60_EVIDENCE_ROOT:-$PHASE60_ROOT/scripts/phase60/evidence}"
PHASE60_RUN_DIR="$PHASE60_EVIDENCE_ROOT/$PHASE60_RUN_ID"
mkdir -p "$PHASE60_RUN_DIR"

phase_setup() {
  PHASE60_PHASE="$1"
  PHASE60_PHASE_NAME="$2"
  PHASE60_PHASE_DIR="$PHASE60_RUN_DIR/$PHASE60_PHASE"
  PHASE60_PHASE_LOG="$PHASE60_PHASE_DIR/phase.log"
  PHASE60_PHASE_STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  mkdir -p "$PHASE60_PHASE_DIR"
  export PHASE60_PHASE PHASE60_PHASE_NAME PHASE60_PHASE_DIR PHASE60_PHASE_LOG PHASE60_PHASE_STARTED_AT
  printf '[%s] %s — %s\n' "$PHASE60_PHASE" "$PHASE60_PHASE_NAME" "$PHASE60_PHASE_STARTED_AT" | tee "$PHASE60_PHASE_LOG"
}

phase_log() {
  printf '[%s] %s\n' "${PHASE60_PHASE:-phase60}" "$*" | tee -a "${PHASE60_PHASE_LOG:-/dev/stderr}"
}

phase_run() {
  local label="$1"; shift
  phase_log "RUN $label"
  {
    printf '+ '
    printf '%q ' "$@"
    printf '\n'
    "$@"
  } 2>&1 | tee -a "$PHASE60_PHASE_LOG"
}

phase_require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    phase_log "ERROR required command is missing: $1"
    return 69
  }
}

phase_require_file() {
  [[ -f "$1" ]] || {
    phase_log "ERROR required file is missing: $1"
    return 2
  }
}

phase_require_runtime_confirmation() {
  [[ "${TARGET_ENVIRONMENT:-}" == "uat" ]] || {
    phase_log "ERROR TARGET_ENVIRONMENT must equal uat"
    return 64
  }
  [[ "${CONFIRM_UAT_DRILLS:-}" == "yes" ]] || {
    phase_log "ERROR CONFIRM_UAT_DRILLS=yes is required"
    return 64
  }
  [[ "${PHASE60_EXECUTE_RUNTIME:-}" == "true" ]] || {
    phase_log "ERROR PHASE60_EXECUTE_RUNTIME=true is required"
    return 64
  }
}

phase_finalize() {
  local status="$1"
  local exit_code="$2"
  local message="${3:-}"
  local finished_at
  finished_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  # Record the terminal status before hashing phase.log so result.json never
  # contains a stale checksum for its own execution log.
  phase_log "RESULT $status — $message"
  python3 "$PHASE60_SCRIPT_DIR/write_phase_result.py" \
    --phase "$PHASE60_PHASE" \
    --name "$PHASE60_PHASE_NAME" \
    --status "$status" \
    --exit-code "$exit_code" \
    --started-at "$PHASE60_PHASE_STARTED_AT" \
    --finished-at "$finished_at" \
    --message "$message" \
    --root "$PHASE60_ROOT" \
    --phase-dir "$PHASE60_PHASE_DIR" \
    --output "$PHASE60_PHASE_DIR/result.json"
}

phase_is_preflight() {
  [[ "${PHASE60_PREFLIGHT_ONLY:-false}" == "true" ]]
}
