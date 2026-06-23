#!/usr/bin/env bash
set -Eeuo pipefail

PHASE61_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export PYTHONDONTWRITEBYTECODE=1
PHASE61_ROOT="$(cd "$PHASE61_SCRIPT_DIR/../.." && pwd)"
PHASE61_RUN_ID="${PHASE61_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
PHASE61_EVIDENCE_ROOT="${PHASE61_EVIDENCE_ROOT:-$PHASE61_ROOT/scripts/phase61/evidence}"
PHASE61_RUN_DIR="$PHASE61_EVIDENCE_ROOT/$PHASE61_RUN_ID"
mkdir -p "$PHASE61_RUN_DIR"

phase_setup() {
  PHASE61_PHASE="$1"
  PHASE61_PHASE_NAME="$2"
  PHASE61_PHASE_DIR="$PHASE61_RUN_DIR/$PHASE61_PHASE"
  PHASE61_PHASE_LOG="$PHASE61_PHASE_DIR/phase.log"
  PHASE61_PHASE_STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  mkdir -p "$PHASE61_PHASE_DIR"
  export PHASE61_PHASE PHASE61_PHASE_NAME PHASE61_PHASE_DIR PHASE61_PHASE_LOG PHASE61_PHASE_STARTED_AT
  printf '[%s] %s — %s\n' "$PHASE61_PHASE" "$PHASE61_PHASE_NAME" "$PHASE61_PHASE_STARTED_AT" | tee "$PHASE61_PHASE_LOG"
}

phase_log() { printf '[%s] %s\n' "${PHASE61_PHASE:-phase61}" "$*" | tee -a "${PHASE61_PHASE_LOG:-/dev/stderr}"; }

phase_run() {
  local label="$1"; shift
  phase_log "RUN $label"
  { printf '+ '; printf '%q ' "$@"; printf '\n'; "$@"; } 2>&1 | tee -a "$PHASE61_PHASE_LOG"
}

phase_require_command() { command -v "$1" >/dev/null 2>&1 || { phase_log "ERROR missing command: $1"; return 69; }; }
phase_require_file() { [[ -f "$1" ]] || { phase_log "ERROR missing file: $1"; return 2; }; }
phase_is_preflight() { [[ "${PHASE61_PREFLIGHT_ONLY:-false}" == "true" ]]; }

phase_require_uat_confirmation() {
  [[ "${TARGET_ENVIRONMENT:-}" == "uat" ]] || { phase_log "ERROR TARGET_ENVIRONMENT=uat is required"; return 64; }
  [[ "${PHASE61_EXECUTE_RUNTIME:-}" == "true" ]] || { phase_log "ERROR PHASE61_EXECUTE_RUNTIME=true is required"; return 64; }
  [[ "${CONFIRM_UAT_DRILLS:-}" == "yes" ]] || { phase_log "ERROR CONFIRM_UAT_DRILLS=yes is required"; return 64; }
}

phase_require_operator_attestation() {
  local file="$1"
  [[ -f "$file" ]] || { phase_log "ERROR operator attestation is missing: $file"; return 66; }
}

phase_finalize() {
  local status="$1" exit_code="$2" message="${3:-}"
  local finished_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  phase_log "RESULT $status — $message"
  python3 "$PHASE61_SCRIPT_DIR/write_phase_result.py" \
    --phase "$PHASE61_PHASE" --name "$PHASE61_PHASE_NAME" --status "$status" \
    --exit-code "$exit_code" --started-at "$PHASE61_PHASE_STARTED_AT" --finished-at "$finished_at" \
    --message "$message" --root "$PHASE61_ROOT" --phase-dir "$PHASE61_PHASE_DIR" \
    --output "$PHASE61_PHASE_DIR/result.json"
}
