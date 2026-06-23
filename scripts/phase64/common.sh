#!/usr/bin/env bash
set -Eeuo pipefail

PHASE64_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PHASE64_ROOT="$(cd "$PHASE64_SCRIPT_DIR/../.." && pwd)"
export PYTHONDONTWRITEBYTECODE=1
PHASE64_RUN_ID="${PHASE64_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
PHASE64_EVIDENCE_ROOT="${PHASE64_EVIDENCE_ROOT:-$PHASE64_ROOT/build/phase64-evidence}"
PHASE64_RUN_DIR="$PHASE64_EVIDENCE_ROOT/$PHASE64_RUN_ID"
PHASE64_CONFIG="${PHASE64_CONFIG:-$PHASE64_ROOT/config/phase64-entry-gate.yaml}"
mkdir -p "$PHASE64_RUN_DIR"
export PHASE64_SCRIPT_DIR PHASE64_ROOT PHASE64_RUN_ID PHASE64_EVIDENCE_ROOT PHASE64_RUN_DIR PHASE64_CONFIG

phase64_now() { date -u +%Y-%m-%dT%H:%M:%SZ; }
phase64_is_preflight() { [[ "${PHASE64_PREFLIGHT_ONLY:-false}" == "true" ]]; }

phase64_setup() {
  PHASE64_PHASE="$1"
  PHASE64_PHASE_NAME="$2"
  PHASE64_PHASE_DIR="$PHASE64_RUN_DIR/$PHASE64_PHASE"
  PHASE64_PHASE_LOG="$PHASE64_PHASE_DIR/phase.log"
  PHASE64_PHASE_STARTED_AT="$(phase64_now)"
  mkdir -p "$PHASE64_PHASE_DIR"
  printf '[%s] %s — %s\n' "$PHASE64_PHASE" "$PHASE64_PHASE_NAME" "$PHASE64_PHASE_STARTED_AT" | tee "$PHASE64_PHASE_LOG"
  export PHASE64_PHASE PHASE64_PHASE_NAME PHASE64_PHASE_DIR PHASE64_PHASE_LOG PHASE64_PHASE_STARTED_AT
}

phase64_log() { printf '[%s] %s\n' "${PHASE64_PHASE:-phase64}" "$*" | tee -a "${PHASE64_PHASE_LOG:-/dev/stderr}"; }
phase64_require_file() { [[ -f "$1" ]] || { phase64_log "ERROR missing file: $1"; return 2; }; }
phase64_require_dir() { [[ -d "$1" ]] || { phase64_log "ERROR missing directory: $1"; return 2; }; }
phase64_require_command() { command -v "$1" >/dev/null 2>&1 || { phase64_log "ERROR missing command: $1"; return 69; }; }

phase64_run() {
  local label="$1"; shift
  phase64_log "RUN $label"
  { printf '+ '; printf '%q ' "$@"; printf '\n'; "$@"; } 2>&1 | tee -a "$PHASE64_PHASE_LOG"
}

phase64_require_release_identity() {
  [[ "${TARGET_ENVIRONMENT:-}" == "uat" ]] || { phase64_log "ERROR TARGET_ENVIRONMENT=uat is required"; return 64; }
  [[ "${PHASE64_EXECUTE_RUNTIME:-}" == "true" ]] || { phase64_log "ERROR PHASE64_EXECUTE_RUNTIME=true is required"; return 64; }
  [[ "${CONFIRM_UAT_EVIDENCE:-}" == "yes" ]] || { phase64_log "ERROR CONFIRM_UAT_EVIDENCE=yes is required"; return 64; }
  : "${RELEASE_REFERENCE:?RELEASE_REFERENCE is required}"
  : "${RELEASE_GIT_COMMIT:?RELEASE_GIT_COMMIT is required}"
  : "${APPLICATION_IMAGE_DIGEST:?APPLICATION_IMAGE_DIGEST is required}"
  : "${MIGRATION_IMAGE_DIGEST:?MIGRATION_IMAGE_DIGEST is required}"
  [[ "$RELEASE_REFERENCE" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{2,127}$ ]] || { phase64_log "ERROR invalid RELEASE_REFERENCE"; return 64; }
  [[ "$RELEASE_GIT_COMMIT" =~ ^[a-f0-9]{40}$ ]] || { phase64_log "ERROR RELEASE_GIT_COMMIT must be 40 lowercase hex"; return 64; }
  [[ "$APPLICATION_IMAGE_DIGEST" =~ ^sha256:[a-f0-9]{64}$ ]] || { phase64_log "ERROR invalid APPLICATION_IMAGE_DIGEST"; return 64; }
  [[ "$MIGRATION_IMAGE_DIGEST" =~ ^sha256:[a-f0-9]{64}$ ]] || { phase64_log "ERROR invalid MIGRATION_IMAGE_DIGEST"; return 64; }
}

phase64_copy_tree() {
  local source="$1" destination="$2"
  phase64_require_dir "$source"
  rm -rf "$destination"
  mkdir -p "$(dirname "$destination")"
  cp -a "$source" "$destination"
}

phase64_finalize() {
  local status="$1" exit_code="$2" message="${3:-}"
  phase64_log "RESULT $status — $message"
  python3 "$PHASE64_SCRIPT_DIR/write_phase_result.py" \
    --phase "$PHASE64_PHASE" --name "$PHASE64_PHASE_NAME" --status "$status" \
    --exit-code "$exit_code" --started-at "$PHASE64_PHASE_STARTED_AT" --finished-at "$(phase64_now)" \
    --message "$message" --run-dir "$PHASE64_RUN_DIR" --phase-dir "$PHASE64_PHASE_DIR" \
    --output "$PHASE64_PHASE_DIR/result.json"
}
