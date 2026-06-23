#!/usr/bin/env bash
set -Eeuo pipefail

PHASE63_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PHASE63_ROOT="${PHASE63_ROOT:-$(cd "$PHASE63_SCRIPT_DIR/../.." && pwd)}"
PHASE63_MODE="${PHASE63_MODE:-preflight}"
PHASE63_RUN_ID="${PHASE63_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)-$(git -C "$PHASE63_ROOT" rev-parse --short HEAD 2>/dev/null || printf nogit)}"
PHASE63_EVIDENCE_ROOT="${PHASE63_EVIDENCE_ROOT:-$PHASE63_ROOT/evidence/phase63}"
PHASE63_RUN_DIR="$PHASE63_EVIDENCE_ROOT/$PHASE63_RUN_ID"
PHASE63_CONTINUE_ON_FAILURE="${PHASE63_CONTINUE_ON_FAILURE:-true}"
export PYTHONDONTWRITEBYTECODE=1
export PHASE63_ROOT PHASE63_MODE PHASE63_RUN_ID PHASE63_EVIDENCE_ROOT PHASE63_RUN_DIR
mkdir -p "$PHASE63_RUN_DIR"

phase63_load_env() {
  local env_file="${PHASE63_ENV_FILE:-$PHASE63_ROOT/config/phase63/execution.env}"
  if [[ -f "$env_file" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$env_file"
    set +a
  fi
}

phase63_setup() {
  PHASE63_PHASE="$1"
  PHASE63_PHASE_NAME="$2"
  PHASE63_PHASE_DIR="$PHASE63_RUN_DIR/$PHASE63_PHASE"
  PHASE63_PHASE_LOG="$PHASE63_PHASE_DIR/phase.log"
  PHASE63_PHASE_STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  mkdir -p "$PHASE63_PHASE_DIR"
  printf '[%s] %s — %s — mode=%s\n' "$PHASE63_PHASE" "$PHASE63_PHASE_NAME" "$PHASE63_PHASE_STARTED_AT" "$PHASE63_MODE" | tee "$PHASE63_PHASE_LOG"
  export PHASE63_PHASE PHASE63_PHASE_NAME PHASE63_PHASE_DIR PHASE63_PHASE_LOG PHASE63_PHASE_STARTED_AT
}

phase63_log() { printf '[%s] %s\n' "${PHASE63_PHASE:-phase63}" "$*" | tee -a "${PHASE63_PHASE_LOG:-/dev/stderr}"; }
phase63_require_file() { [[ -f "$1" ]] || { phase63_log "ERROR missing file: $1"; return 2; }; }
phase63_require_dir() { [[ -d "$1" ]] || { phase63_log "ERROR missing directory: $1"; return 2; }; }
phase63_require_command() { command -v "$1" >/dev/null 2>&1 || { phase63_log "ERROR missing command: $1"; return 69; }; }
phase63_is_preflight() { [[ "$PHASE63_MODE" == preflight ]]; }
phase63_is_repo() { [[ "$PHASE63_MODE" == repo ]]; }
phase63_is_full() { [[ "$PHASE63_MODE" == full ]]; }

phase63_require_uat_confirmation() {
  [[ "${TARGET_ENVIRONMENT:-}" == uat ]] || { phase63_log 'ERROR TARGET_ENVIRONMENT=uat is required'; return 64; }
  [[ "${PHASE63_EXECUTE_RUNTIME:-}" == true ]] || { phase63_log 'ERROR PHASE63_EXECUTE_RUNTIME=true is required'; return 64; }
  [[ "${CONFIRM_UAT_DRILLS:-}" == yes ]] || { phase63_log 'ERROR CONFIRM_UAT_DRILLS=yes is required'; return 64; }
}

phase63_require_attestation() {
  local path="$1"
  [[ -f "$path" ]] || { phase63_log "ERROR signed attestation missing: $path"; return 66; }
}

phase63_run() {
  local label="$1"; shift
  phase63_log "RUN $label"
  { printf '+ '; printf '%q ' "$@"; printf '\n'; "$@"; } 2>&1 | tee -a "$PHASE63_PHASE_LOG"
}

phase63_capture() {
  local label="$1" output="$2"; shift 2
  phase63_log "CAPTURE $label -> $output"
  mkdir -p "$(dirname "$output")"
  { printf '+ '; printf '%q ' "$@"; printf '\n'; "$@"; } >"$output" 2>"$output.stderr"
}

phase63_controlled_command() {
  local label="$1" command_value="$2" output="$3"
  [[ -n "$command_value" ]] || { phase63_log "ERROR missing command hook: $label"; return 64; }
  mkdir -p "$(dirname "$output")"
  printf '%s\n' "$label" > "$output.command-label.txt"
  timeout "${PHASE63_COMMAND_TIMEOUT_SECONDS:-3600}" bash -Eeuo pipefail -c "$command_value" >"$output" 2>&1
}

phase63_write_context() {
  local context_dir="$PHASE63_RUN_DIR/context"
  mkdir -p "$context_dir"
  {
    echo "run_id=$PHASE63_RUN_ID"
    echo "mode=$PHASE63_MODE"
    echo "generated_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "git_commit=$(git -C "$PHASE63_ROOT" rev-parse HEAD 2>/dev/null || printf unavailable)"
    echo "git_branch=$(git -C "$PHASE63_ROOT" branch --show-current 2>/dev/null || printf unavailable)"
    echo "git_tree=$(git -C "$PHASE63_ROOT" status --porcelain=v1 --untracked-files=no 2>/dev/null | sha256sum | awk '{print $1}' || printf unavailable)"
    echo "hostname=$(hostname 2>/dev/null || printf unavailable)"
    echo "kernel=$(uname -srmo 2>/dev/null || printf unavailable)"
  } > "$context_dir/runtime-context.env"
}

phase63_finalize() {
  local status="$1" exit_code="$2" message="${3:-}"
  local finished_at
  finished_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  phase63_log "RESULT $status — $message"
  python3 "$PHASE63_SCRIPT_DIR/write_phase_result.py" \
    --phase "$PHASE63_PHASE" --name "$PHASE63_PHASE_NAME" --status "$status" \
    --exit-code "$exit_code" --started-at "$PHASE63_PHASE_STARTED_AT" --finished-at "$finished_at" \
    --message "$message" --root "$PHASE63_ROOT" --phase-dir "$PHASE63_PHASE_DIR" \
    --output "$PHASE63_PHASE_DIR/result.json"
}
