#!/usr/bin/env bash
set -Eeuo pipefail

GOLIVE_PLAN="${GOLIVE_PLAN:-config/phase55-golive-plan.yaml}"
GOLIVE_THRESHOLDS="${GOLIVE_THRESHOLDS:-config/phase55-thresholds.yaml}"
GOLIVE_ROOT="${GOLIVE_ROOT:-build/phase55-golive}"

live_die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
live_require_command() { command -v "$1" >/dev/null 2>&1 || live_die "required command not found: $1"; }
live_require_image_repository() {
  local value="$1" label="${2:-image repository}"
  [[ -n "$value" && "$value" != *"@"* && "$value" != *":latest" && "$value" != *[[:space:]]* ]] || \
    live_die "invalid $label"
}
live_now() { date -u +%Y-%m-%dT%H:%M:%SZ; }

live_require_release_identity() {
  : "${GOLIVE_ENVIRONMENT:?GOLIVE_ENVIRONMENT is required}"
  : "${RELEASE_REFERENCE:?RELEASE_REFERENCE is required}"
  : "${RELEASE_RC_ID:?RELEASE_RC_ID is required}"
  : "${RELEASE_GIT_COMMIT:?RELEASE_GIT_COMMIT is required}"
  : "${RELEASE_APP_IMAGE_DIGEST:?RELEASE_APP_IMAGE_DIGEST is required}"
  : "${RELEASE_MIGRATION_IMAGE_DIGEST:?RELEASE_MIGRATION_IMAGE_DIGEST is required}"
  case "$GOLIVE_ENVIRONMENT" in release|production-dry-run|production|hypercare) ;; *) live_die "unsupported GOLIVE_ENVIRONMENT: $GOLIVE_ENVIRONMENT" ;; esac
  [[ "$RELEASE_REFERENCE" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{2,127}$ ]] || live_die "invalid RELEASE_REFERENCE"
  [[ "$RELEASE_RC_ID" =~ ^switching-[A-Za-z0-9][A-Za-z0-9._-]{2,95}$ ]] || live_die "invalid RELEASE_RC_ID"
  [[ "$RELEASE_GIT_COMMIT" =~ ^[a-f0-9]{40}$ ]] || live_die "RELEASE_GIT_COMMIT must be a full lowercase SHA"
  [[ "$RELEASE_APP_IMAGE_DIGEST" =~ ^sha256:[a-f0-9]{64}$ ]] || live_die "invalid RELEASE_APP_IMAGE_DIGEST"
  [[ "$RELEASE_MIGRATION_IMAGE_DIGEST" =~ ^sha256:[a-f0-9]{64}$ ]] || live_die "invalid RELEASE_MIGRATION_IMAGE_DIGEST"
  [[ -f "$GOLIVE_PLAN" ]] || live_die "missing Go-Live plan: $GOLIVE_PLAN"
  [[ -f "$GOLIVE_THRESHOLDS" ]] || live_die "missing Go-Live thresholds: $GOLIVE_THRESHOLDS"
}

live_require_environment() {
  local expected="$1"
  [[ "$GOLIVE_ENVIRONMENT" == "$expected" ]] || live_die "$PHASE_ID must run with GOLIVE_ENVIRONMENT=$expected"
}

live_require_production_confirmation() {
  [[ "${PRODUCTION_EXECUTION_CONFIRMATION:-}" == I_UNDERSTAND_THIS_OPERATES_ON_PRODUCTION ]] || \
    live_die "set PRODUCTION_EXECUTION_CONFIRMATION=I_UNDERSTAND_THIS_OPERATES_ON_PRODUCTION"
}

live_release_identity_json() {
  python3 - "$RELEASE_REFERENCE" "$RELEASE_RC_ID" "$RELEASE_GIT_COMMIT" "$RELEASE_APP_IMAGE_DIGEST" "$RELEASE_MIGRATION_IMAGE_DIGEST" "$GOLIVE_ENVIRONMENT" <<'PY'
import json, sys
reference, rc_id, commit, app_digest, migration_digest, environment = sys.argv[1:]
print(json.dumps({
  "reference": reference,
  "releaseCandidateId": rc_id,
  "gitCommit": commit,
  "applicationImageDigest": app_digest,
  "migrationImageDigest": migration_digest,
  "environment": environment,
}, sort_keys=True))
PY
}

require_phase_pass() {
  local phase result
  for phase in "$@"; do
    result="$GOLIVE_ROOT/phases/$phase/result.json"
    [[ -f "$result" ]] || live_die "required prerequisite phase is missing: $phase"
    python3 - "$result" "$phase" "$RELEASE_REFERENCE" "$RELEASE_RC_ID" "$RELEASE_GIT_COMMIT" "$RELEASE_APP_IMAGE_DIGEST" "$RELEASE_MIGRATION_IMAGE_DIGEST" <<'PY'
import json, pathlib, sys
path, phase, reference, rc_id, commit, app_digest, migration_digest = sys.argv[1:]
data = json.loads(pathlib.Path(path).read_text(encoding="utf-8"))
release = data.get("release", {})
if data.get("phase") != phase or data.get("status") != "PASS":
    raise SystemExit(f"prerequisite {phase} is not PASS")
expected = {
    "reference": reference,
    "releaseCandidateId": rc_id,
    "gitCommit": commit,
    "applicationImageDigest": app_digest,
    "migrationImageDigest": migration_digest,
}
for key, value in expected.items():
    if release.get(key) != value:
        raise SystemExit(f"prerequisite {phase} release identity mismatch: {key}")
PY
  done
}

phase_begin() {
  PHASE_ID="$1"; PHASE_NAME="$2"
  [[ "$PHASE_ID" =~ ^55[A-J]$ ]] || live_die "invalid phase id: $PHASE_ID"
  PHASE_DIR="$GOLIVE_ROOT/phases/$PHASE_ID"
  if [[ -d "$PHASE_DIR" ]] && find "$PHASE_DIR" -mindepth 1 -print -quit | grep -q .; then
    [[ "${GOLIVE_RERUN_CONFIRMATION:-}" == I_UNDERSTAND_THIS_ARCHIVES_THE_PREVIOUS_ATTEMPT ]] || \
      live_die "phase $PHASE_ID already has partial or complete evidence; confirm rerun to archive it"
    archive="$GOLIVE_ROOT/attempts/${PHASE_ID}-$(date -u +%Y%m%dT%H%M%SZ)-$$"
    mkdir -p "$(dirname "$archive")"
    mv "$PHASE_DIR" "$archive"
  fi
  PHASE_LOG_DIR="$PHASE_DIR/logs"
  PHASE_CHECKS_FILE="$PHASE_DIR/checks.jsonl"
  PHASE_STARTED_AT="$(live_now)"
  mkdir -p "$PHASE_LOG_DIR"
  : > "$PHASE_CHECKS_FILE"
  export PHASE_ID PHASE_NAME PHASE_DIR PHASE_LOG_DIR PHASE_CHECKS_FILE PHASE_STARTED_AT
}

record_check() {
  local id="$1" status="$2" exit_code="$3" log_rel="$4"
  python3 - "$PHASE_CHECKS_FILE" "$id" "$status" "$exit_code" "$log_rel" <<'PY'
import json, pathlib, sys
path, check_id, status, code, log_rel = sys.argv[1:]
with pathlib.Path(path).open("a", encoding="utf-8") as stream:
    stream.write(json.dumps({"id": check_id, "status": status, "exitCode": int(code), "log": log_rel}, sort_keys=True) + "\n")
PY
}

run_check() {
  local id="$1"; shift
  [[ "$id" =~ ^[a-z0-9][a-z0-9._-]*$ ]] || live_die "invalid check id: $id"
  local log="$PHASE_LOG_DIR/$id.log" rc status
  printf '[%s] START %s\n' "$(live_now)" "$id" > "$log"
  set +e
  "$@" >> "$log" 2>&1
  rc=$?
  set -e
  if (( rc == 0 )); then status=PASS; else status=FAIL; fi
  printf '[%s] %s %s exit=%s\n' "$(live_now)" "$status" "$id" "$rc" >> "$log"
  record_check "$id" "$status" "$rc" "phases/$PHASE_ID/logs/$id.log"
  return "$rc"
}

write_phase_result() {
  local forced_status="${1:-}"
  local ended="$(live_now)"
  python3 - "$PHASE_DIR/result.json" "$PHASE_CHECKS_FILE" "$PHASE_ID" "$PHASE_NAME" \
    "$PHASE_STARTED_AT" "$ended" "$forced_status" "$RELEASE_REFERENCE" "$RELEASE_RC_ID" \
    "$RELEASE_GIT_COMMIT" "$RELEASE_APP_IMAGE_DIGEST" "$RELEASE_MIGRATION_IMAGE_DIGEST" \
    "$GOLIVE_ENVIRONMENT" <<'PY'
import json, pathlib, sys
(out, checks_file, phase, name, started, ended, forced, reference, rc_id, commit, app_digest, migration_digest, environment) = sys.argv[1:]
checks = [json.loads(line) for line in pathlib.Path(checks_file).read_text(encoding="utf-8").splitlines() if line.strip()]
calculated = "PASS" if checks and all(c["status"] == "PASS" for c in checks) else "FAIL"
# A caller may force failure, but may never force PASS over a failed or empty check set.
status = "FAIL" if forced == "FAIL" else calculated
doc = {
  "schemaVersion": 1,
  "phase": phase,
  "name": name,
  "status": status,
  "startedAt": started,
  "endedAt": ended,
  "release": {
    "reference": reference,
    "releaseCandidateId": rc_id,
    "gitCommit": commit,
    "applicationImageDigest": app_digest,
    "migrationImageDigest": migration_digest,
    "environment": environment,
  },
  "checks": checks,
}
pathlib.Path(out).write_text(json.dumps(doc, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY
  python3 - "$PHASE_DIR/result.json" <<'PY'
import json, sys
raise SystemExit(0 if json.load(open(sys.argv[1], encoding="utf-8"))["status"] == "PASS" else 1)
PY
}

live_sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | awk '{print $1}'; else shasum -a 256 "$1" | awk '{print $1}'; fi
}

live_write_checksum() {
  local file="$1" output="$2"
  mkdir -p "$(dirname "$output")"
  printf '%s  %s\n' "$(live_sha256_file "$file")" "$(basename "$file")" > "$output"
}

live_verify_json_file() {
  python3 - "$1" <<'PY'
import json, pathlib, sys
path = pathlib.Path(sys.argv[1])
json.loads(path.read_text(encoding="utf-8"))
PY
}
