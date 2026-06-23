#!/usr/bin/env bash
set -Eeuo pipefail

PHASE73_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PHASE73_ROOT="$(cd "$PHASE73_SCRIPT_DIR/../.." && pwd)"
PHASE73_POLICY="${PHASE73_POLICY:-$PHASE73_ROOT/config/phase73-chaos-policy.yaml}"
PHASE73_RUN_ID="${PHASE73_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
PHASE73_EVIDENCE_ROOT="${PHASE73_EVIDENCE_ROOT:-$PHASE73_ROOT/build/phase73-evidence}"
PHASE73_RUN_DIR="$PHASE73_EVIDENCE_ROOT/$PHASE73_RUN_ID"
export PYTHONDONTWRITEBYTECODE=1
mkdir -p "$PHASE73_RUN_DIR"

phase73_policy() {
  python3 "$PHASE73_SCRIPT_DIR/policy_value.py" --policy "$PHASE73_POLICY" --path "$1" "${@:2}"
}

phase73_load_policy_environment() {
  local policy_exports
  if [[ "${PHASE73_POLICY_ENV_READY:-false}" != "true" ]]; then
    policy_exports="$(python3 "$PHASE73_SCRIPT_DIR/export_policy_env.py" --policy "$PHASE73_POLICY")"
    eval "$policy_exports"
    export PHASE73_POLICY_ENV_READY=true
  fi
  export PHASE73_NAMESPACE PHASE73_APP_LABEL PHASE73_DEPLOYMENT_NAME PHASE73_BASE_URL PHASE73_HEALTH_URL
  export PHASE73_EXPERIMENT_DURATION PHASE73_STABILIZATION_SECONDS PHASE73_CLEANUP_TIMEOUT_SECONDS PHASE73_COMMAND_TIMEOUT_SECONDS
  export PHASE73_DATABASE_CIDRS_JSON PHASE73_KAFKA_CIDRS_JSON PHASE73_OBJECT_STORAGE_CIDRS_JSON
  export PHASE73_EXTERNAL_API_CIDRS_JSON PHASE73_DNS_PATTERNS_JSON PHASE73_CPU_WORKERS PHASE73_CPU_LOAD_PERCENT
  export PHASE73_MEMORY_WORKERS PHASE73_MEMORY_SIZE PHASE73_PRODUCTION_EXECUTION_ALLOWED PHASE73_APPROVAL_MAX_AGE_MINUTES PHASE73_REQUIRED_SCENARIOS_JSON
  export PHASE73_RUN_ID_SAFE="$(printf '%s' "$PHASE73_RUN_ID" | tr -cs 'a-zA-Z0-9-' '-' | tr '[:upper:]' '[:lower:]' | cut -c1-40)"
}

phase73_setup() {
  PHASE73_PHASE="$1"
  PHASE73_PHASE_NAME="$2"
  PHASE73_PHASE_DIR="$PHASE73_RUN_DIR/$PHASE73_PHASE"
  PHASE73_PHASE_LOG="$PHASE73_PHASE_DIR/phase.log"
  PHASE73_PHASE_STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  mkdir -p "$PHASE73_PHASE_DIR"
  export PHASE73_PHASE PHASE73_PHASE_NAME PHASE73_PHASE_DIR PHASE73_PHASE_LOG PHASE73_PHASE_STARTED_AT
  phase73_load_policy_environment
  printf '[%s] %s — %s\n' "$PHASE73_PHASE" "$PHASE73_PHASE_NAME" "$PHASE73_PHASE_STARTED_AT" | tee "$PHASE73_PHASE_LOG"
}

phase73_log() { printf '[%s] %s\n' "${PHASE73_PHASE:-phase73}" "$*" | tee -a "${PHASE73_PHASE_LOG:-/dev/stderr}"; }
phase73_run() {
  local label="$1"; shift
  phase73_log "RUN $label"
  { printf '+ '; printf '%q ' "$@"; printf '\n'; "$@"; } 2>&1 | tee -a "$PHASE73_PHASE_LOG"
}
phase73_require_command() { command -v "$1" >/dev/null 2>&1 || { phase73_log "ERROR missing command: $1"; return 69; }; }
phase73_require_file() { [[ -f "$1" ]] || { phase73_log "ERROR missing file: $1"; return 2; }; }
phase73_is_preflight() { [[ "${PHASE73_PREFLIGHT_ONLY:-false}" == "true" ]]; }

phase73_require_execution_approval() {
  [[ "${TARGET_ENVIRONMENT:-}" == "uat" ]] || { phase73_log "ERROR TARGET_ENVIRONMENT=uat is required"; return 64; }
  [[ "${PHASE73_EXECUTE_CHAOS:-}" == "true" ]] || { phase73_log "ERROR PHASE73_EXECUTE_CHAOS=true is required"; return 64; }
  [[ -n "${CHAOS_APPROVAL_FILE:-}" && -f "$CHAOS_APPROVAL_FILE" ]] || { phase73_log "ERROR CHAOS_APPROVAL_FILE is required"; return 66; }
  [[ -n "${CHAOS_APPROVAL_TOKEN:-}" ]] || { phase73_log "ERROR CHAOS_APPROVAL_TOKEN is required"; return 66; }
  [[ "$PHASE73_PRODUCTION_EXECUTION_ALLOWED" == "false" ]] || { phase73_log "ERROR policy must deny production execution"; return 65; }
  python3 "$PHASE73_SCRIPT_DIR/validate_approval.py" --approval "$CHAOS_APPROVAL_FILE" --token "$CHAOS_APPROVAL_TOKEN" --maximum-age-minutes "$PHASE73_APPROVAL_MAX_AGE_MINUTES" --required-scenarios-json "$PHASE73_REQUIRED_SCENARIOS_JSON" >/dev/null
  local context
  context="$(kubectl config current-context 2>/dev/null || true)"
  [[ -n "$context" ]] || { phase73_log "ERROR kubectl context is unavailable"; return 69; }
  if [[ "$context" =~ [Pp][Rr][Oo][Dd] ]]; then
    phase73_log "ERROR kubectl context appears to be production: $context"
    return 65
  fi
}

phase73_require_nonempty_json_array() {
  local name="$1" value="$2"
  python3 - "$name" "$value" <<'PY'
import json, sys
name, raw = sys.argv[1], sys.argv[2]
try:
    value = json.loads(raw)
except json.JSONDecodeError as exc:
    raise SystemExit(f"{name} is not valid JSON: {exc}")
if not isinstance(value, list) or not value:
    raise SystemExit(f"{name} must be a non-empty JSON array for execute mode")
PY
}

phase73_finalize() {
  local status="$1" exit_code="$2" message="${3:-}"
  local finished_at target
  finished_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  target="preflight"
  [[ "$status" != "PREPARED" ]] && target="${TARGET_ENVIRONMENT:-uat}"
  phase73_log "RESULT $status — $message"
  python3 "$PHASE73_SCRIPT_DIR/write_phase_result.py" \
    --phase "$PHASE73_PHASE" --name "$PHASE73_PHASE_NAME" --status "$status" \
    --exit-code "$exit_code" --started-at "$PHASE73_PHASE_STARTED_AT" --finished-at "$finished_at" \
    --run-id "$PHASE73_RUN_ID" --target-environment "$target" --message "$message" \
    --phase-dir "$PHASE73_PHASE_DIR" --output "$PHASE73_PHASE_DIR/result.json"
}
