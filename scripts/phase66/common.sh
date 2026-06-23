#!/usr/bin/env bash
set -Eeuo pipefail

PHASE66_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PHASE66_ROOT="$(cd "$PHASE66_SCRIPT_DIR/../.." && pwd)"
export PYTHONDONTWRITEBYTECODE=1
PHASE66_MODE="${PHASE66_MODE:-preflight}"
PHASE66_RUN_ID="${PHASE66_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
PHASE66_EVIDENCE_ROOT="${PHASE66_EVIDENCE_ROOT:-$PHASE66_ROOT/build/phase66-evidence}"
PHASE66_RUN_DIR="$PHASE66_EVIDENCE_ROOT/$PHASE66_RUN_ID"
mkdir -p "$PHASE66_RUN_DIR"
export PHASE66_ROOT PHASE66_MODE PHASE66_RUN_ID PHASE66_EVIDENCE_ROOT PHASE66_RUN_DIR

phase66_setup() {
  PHASE66_PHASE="$1"
  PHASE66_PHASE_NAME="$2"
  PHASE66_PHASE_DIR="$PHASE66_RUN_DIR/$PHASE66_PHASE"
  PHASE66_PHASE_LOG="$PHASE66_PHASE_DIR/phase.log"
  PHASE66_STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  mkdir -p "$PHASE66_PHASE_DIR"
  export PHASE66_PHASE PHASE66_PHASE_NAME PHASE66_PHASE_DIR PHASE66_PHASE_LOG PHASE66_STARTED_AT
  printf '[%s] %s — mode=%s — %s\n' "$PHASE66_PHASE" "$PHASE66_PHASE_NAME" "$PHASE66_MODE" "$PHASE66_STARTED_AT" | tee "$PHASE66_PHASE_LOG"
}

phase66_log() { printf '[%s] %s\n' "${PHASE66_PHASE:-phase66}" "$*" | tee -a "${PHASE66_PHASE_LOG:-/dev/stderr}"; }
phase66_run() {
  local label="$1"; shift
  phase66_log "RUN $label"
  { printf '+ '; printf '%q ' "$@"; printf '\n'; "$@"; } 2>&1 | tee -a "$PHASE66_PHASE_LOG"
}
phase66_capture() {
  local label="$1" output="$2"; shift 2
  phase66_log "RUN $label -> $output"
  mkdir -p "$(dirname "$output")"
  { printf '+ '; printf '%q ' "$@"; printf '\n'; "$@"; } >"$output" 2>&1
  cat "$output" >> "$PHASE66_PHASE_LOG"
}
phase66_require_command() { command -v "$1" >/dev/null 2>&1 || { phase66_log "ERROR missing command: $1"; return 69; }; }
phase66_require_file() { [[ -f "$1" ]] || { phase66_log "ERROR missing file: $1"; return 66; }; }
phase66_is_preflight() { [[ "$PHASE66_MODE" == "preflight" ]]; }
phase66_is_repo() { [[ "$PHASE66_MODE" == "repo" ]]; }
phase66_is_full() { [[ "$PHASE66_MODE" == "full" ]]; }

phase66_require_uat() {
  [[ "${TARGET_ENVIRONMENT:-}" == "uat" ]] || { phase66_log "ERROR TARGET_ENVIRONMENT=uat required"; return 64; }
  [[ "${PHASE66_EXECUTE_RUNTIME:-}" == "true" ]] || { phase66_log "ERROR PHASE66_EXECUTE_RUNTIME=true required"; return 64; }
  [[ "${CONFIRM_UAT_RUNTIME:-}" == "yes" ]] || { phase66_log "ERROR CONFIRM_UAT_RUNTIME=yes required"; return 64; }
}
phase66_require_load_confirmation() {
  [[ "${CONFIRM_UAT_LOAD:-}" == "I_UNDERSTAND_THIS_GENERATES_LOAD" ]] || {
    phase66_log "ERROR CONFIRM_UAT_LOAD=I_UNDERSTAND_THIS_GENERATES_LOAD required"; return 64; }
}
phase66_require_destructive_confirmation() {
  [[ "${CONFIRM_UAT_DESTRUCTIVE:-}" == "I_UNDERSTAND_THIS_IS_DESTRUCTIVE" ]] || {
    phase66_log "ERROR CONFIRM_UAT_DESTRUCTIVE=I_UNDERSTAND_THIS_IS_DESTRUCTIVE required"; return 64; }
}
phase66_require_secret_ceremony_confirmation() {
  [[ "${CONFIRM_SECRET_ROTATION_CEREMONY:-}" == "I_UNDERSTAND_THIS_ROTATES_CREDENTIALS" ]] || {
    phase66_log "ERROR CONFIRM_SECRET_ROTATION_CEREMONY=I_UNDERSTAND_THIS_ROTATES_CREDENTIALS required"; return 64; }
}

phase66_finalize() {
  local status="$1" code="$2" message="${3:-}"
  local finished="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  phase66_log "RESULT $status — $message"
  python3 "$PHASE66_SCRIPT_DIR/write_phase66_result.py" \
    --phase "$PHASE66_PHASE" --name "$PHASE66_PHASE_NAME" --status "$status" \
    --exit-code "$code" --started-at "$PHASE66_STARTED_AT" --finished-at "$finished" \
    --message "$message" --mode "$PHASE66_MODE" --root "$PHASE66_ROOT" \
    --phase-dir "$PHASE66_PHASE_DIR" --output "$PHASE66_PHASE_DIR/result.json"
}
