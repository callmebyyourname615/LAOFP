#!/usr/bin/env bash
set -Eeuo pipefail

PHASE67_POLICY="${PHASE67_POLICY:-config/phase67-production-cutover-policy.yaml}"
PHASE67_ROOT="${PHASE67_ROOT:-build/phase67-production-cutover}"
PHASE55_ROOT="${PHASE55_ROOT:-build/phase55-golive}"
PHASE67_MODE="${PHASE67_MODE:-preflight}"
PHASE67_ENVIRONMENT="${PHASE67_ENVIRONMENT:-release}"

p67_die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
p67_now() { date -u +%Y-%m-%dT%H:%M:%SZ; }
p67_require_command() { command -v "$1" >/dev/null 2>&1 || p67_die "required command not found: $1"; }

p67_require_identity() {
  : "${RELEASE_REFERENCE:?RELEASE_REFERENCE is required}"
  : "${RELEASE_RC_ID:?RELEASE_RC_ID is required}"
  : "${RELEASE_GIT_COMMIT:?RELEASE_GIT_COMMIT is required}"
  : "${RELEASE_APP_IMAGE_DIGEST:?RELEASE_APP_IMAGE_DIGEST is required}"
  : "${RELEASE_MIGRATION_IMAGE_DIGEST:?RELEASE_MIGRATION_IMAGE_DIGEST is required}"
  case "$PHASE67_MODE" in preflight|import|execute) ;; *) p67_die "unsupported PHASE67_MODE: $PHASE67_MODE" ;; esac
  case "$PHASE67_ENVIRONMENT" in release|production|hypercare) ;; *) p67_die "unsupported PHASE67_ENVIRONMENT: $PHASE67_ENVIRONMENT" ;; esac
  [[ "$RELEASE_REFERENCE" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{2,127}$ ]] || p67_die "invalid RELEASE_REFERENCE"
  [[ "$RELEASE_RC_ID" =~ ^switching-[A-Za-z0-9][A-Za-z0-9._-]{2,95}$ ]] || p67_die "invalid RELEASE_RC_ID"
  [[ "$RELEASE_GIT_COMMIT" =~ ^[a-f0-9]{40}$ ]] || p67_die "RELEASE_GIT_COMMIT must be a full lowercase SHA"
  [[ "$RELEASE_APP_IMAGE_DIGEST" =~ ^sha256:[a-f0-9]{64}$ ]] || p67_die "invalid RELEASE_APP_IMAGE_DIGEST"
  [[ "$RELEASE_MIGRATION_IMAGE_DIGEST" =~ ^sha256:[a-f0-9]{64}$ ]] || p67_die "invalid RELEASE_MIGRATION_IMAGE_DIGEST"
  [[ -f "$PHASE67_POLICY" ]] || p67_die "missing policy: $PHASE67_POLICY"
}

p67_require_environment() {
  local expected="$1"
  [[ "$PHASE67_ENVIRONMENT" == "$expected" ]] || p67_die "$PHASE_ID requires PHASE67_ENVIRONMENT=$expected"
}

p67_require_production_confirmation() {
  if [[ "$PHASE67_MODE" == execute ]]; then
    [[ "${PRODUCTION_EXECUTION_CONFIRMATION:-}" == I_UNDERSTAND_THIS_OPERATES_ON_PRODUCTION ]] || \
      p67_die "set PRODUCTION_EXECUTION_CONFIRMATION=I_UNDERSTAND_THIS_OPERATES_ON_PRODUCTION"
  fi
}

p67_release_args() {
  printf '%s\n' \
    --reference "$RELEASE_REFERENCE" \
    --rc-id "$RELEASE_RC_ID" \
    --git-commit "$RELEASE_GIT_COMMIT" \
    --application-digest "$RELEASE_APP_IMAGE_DIGEST" \
    --migration-digest "$RELEASE_MIGRATION_IMAGE_DIGEST" \
    --environment "$PHASE67_ENVIRONMENT" \
    --mode "$PHASE67_MODE"
}

p67_begin() {
  PHASE_ID="$1"; PHASE_NAME="$2"
  [[ "$PHASE_ID" =~ ^67[A-J]$ ]] || p67_die "invalid phase id: $PHASE_ID"
  PHASE_DIR="$PHASE67_ROOT/phases/$PHASE_ID"
  if [[ -d "$PHASE_DIR" ]] && find "$PHASE_DIR" -mindepth 1 -print -quit | grep -q .; then
    if [[ "${PHASE67_RERUN_CONFIRMATION:-}" != I_UNDERSTAND_THIS_ARCHIVES_THE_PREVIOUS_ATTEMPT ]]; then
      p67_die "phase $PHASE_ID already has evidence; set PHASE67_RERUN_CONFIRMATION to archive it"
    fi
    local archive="$PHASE67_ROOT/attempts/${PHASE_ID}-$(date -u +%Y%m%dT%H%M%SZ)-$$"
    mkdir -p "$(dirname "$archive")"
    mv "$PHASE_DIR" "$archive"
  fi
  PHASE_LOG_DIR="$PHASE_DIR/logs"
  PHASE_CHECKS_FILE="$PHASE_DIR/checks.jsonl"
  PHASE_STARTED_AT="$(p67_now)"
  mkdir -p "$PHASE_LOG_DIR"
  : > "$PHASE_CHECKS_FILE"
  export PHASE_ID PHASE_NAME PHASE_DIR PHASE_LOG_DIR PHASE_CHECKS_FILE PHASE_STARTED_AT
}

p67_record_check() {
  local id="$1" status="$2" code="$3" log_rel="$4"
  python3 - "$PHASE_CHECKS_FILE" "$id" "$status" "$code" "$log_rel" <<'PY'
import json, pathlib, sys
path, check_id, status, code, log_rel = sys.argv[1:]
with pathlib.Path(path).open("a", encoding="utf-8") as stream:
    stream.write(json.dumps({"id": check_id, "status": status, "exitCode": int(code), "log": log_rel}, sort_keys=True) + "\n")
PY
}

p67_run_check() {
  local id="$1"; shift
  [[ "$id" =~ ^[a-z0-9][a-z0-9._-]*$ ]] || p67_die "invalid check id: $id"
  local log="$PHASE_LOG_DIR/$id.log" rc status
  printf '[%s] START %s\n' "$(p67_now)" "$id" > "$log"
  set +e
  "$@" >> "$log" 2>&1
  rc=$?
  set -e
  if (( rc == 0 )); then
    status="PASS"
  elif (( rc == 3 )) && [[ "$PHASE67_MODE" == preflight ]]; then
    status="PREPARED"
    rc=0
  else
    status="FAIL"
  fi
  printf '[%s] %s %s exit=%s\n' "$(p67_now)" "$status" "$id" "$rc" >> "$log"
  p67_record_check "$id" "$status" "$rc" "phases/$PHASE_ID/logs/$id.log"
  return "$rc"
}

p67_write_result() {
  local forced="${1:-}" ended
  ended="$(p67_now)"
  python3 scripts/phase67/phase67_control.py result \
    --output "$PHASE_DIR/result.json" \
    --checks "$PHASE_CHECKS_FILE" \
    --phase "$PHASE_ID" --name "$PHASE_NAME" \
    --started-at "$PHASE_STARTED_AT" --ended-at "$ended" \
    --forced-status "$forced" \
    --reference "$RELEASE_REFERENCE" --rc-id "$RELEASE_RC_ID" \
    --git-commit "$RELEASE_GIT_COMMIT" \
    --application-digest "$RELEASE_APP_IMAGE_DIGEST" \
    --migration-digest "$RELEASE_MIGRATION_IMAGE_DIGEST" \
    --environment "$PHASE67_ENVIRONMENT" --mode "$PHASE67_MODE"
}

p67_require_phase67_pass() {
  local args=(prerequisites --root "$PHASE67_ROOT") phase
  for phase in "$@"; do args+=(--phase "$phase"); done
  python3 scripts/phase67/phase67_control.py "${args[@]}" \
    --reference "$RELEASE_REFERENCE" --rc-id "$RELEASE_RC_ID" \
    --git-commit "$RELEASE_GIT_COMMIT" \
    --application-digest "$RELEASE_APP_IMAGE_DIGEST" \
    --migration-digest "$RELEASE_MIGRATION_IMAGE_DIGEST"
}

p67_require_phase55_pass() {
  local phase="$1"
  python3 scripts/phase67/phase67_control.py prerequisite \
    --result "$PHASE55_ROOT/phases/$phase/result.json" --phase "$phase" \
    --reference "$RELEASE_REFERENCE" --rc-id "$RELEASE_RC_ID" \
    --git-commit "$RELEASE_GIT_COMMIT" \
    --application-digest "$RELEASE_APP_IMAGE_DIGEST" \
    --migration-digest "$RELEASE_MIGRATION_IMAGE_DIGEST"
}
