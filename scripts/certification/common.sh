#!/usr/bin/env bash
set -Eeuo pipefail

CERTIFICATION_PLAN="${CERTIFICATION_PLAN:-config/phase54-certification-plan.yaml}"
CERTIFICATION_THRESHOLDS="${CERTIFICATION_THRESHOLDS:-config/phase54-thresholds.yaml}"
CERTIFICATION_ROOT="${CERTIFICATION_ROOT:-build/phase54-certification}"

cert_die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
cert_require_command() { command -v "$1" >/dev/null 2>&1 || cert_die "required command not found: $1"; }
cert_now() { date -u +%Y-%m-%dT%H:%M:%SZ; }

cert_require_release_identity() {
  : "${CERTIFICATION_ENVIRONMENT:?CERTIFICATION_ENVIRONMENT is required}"
  : "${RELEASE_REFERENCE:?RELEASE_REFERENCE is required}"
  : "${RELEASE_GIT_COMMIT:?RELEASE_GIT_COMMIT is required}"
  : "${RELEASE_IMAGE_DIGEST:?RELEASE_IMAGE_DIGEST is required}"
  case "$CERTIFICATION_ENVIRONMENT" in uat|performance|dr) ;; *) cert_die "certification is prohibited in environment: $CERTIFICATION_ENVIRONMENT" ;; esac
  [[ "$RELEASE_REFERENCE" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{2,127}$ ]] || cert_die "invalid RELEASE_REFERENCE"
  [[ "$RELEASE_GIT_COMMIT" =~ ^[a-f0-9]{40}$ ]] || cert_die "RELEASE_GIT_COMMIT must be a full lowercase SHA"
  [[ "$RELEASE_IMAGE_DIGEST" =~ ^sha256:[a-f0-9]{64}$ ]] || cert_die "RELEASE_IMAGE_DIGEST must be sha256:<64 lowercase hex>"
  [[ -f "$CERTIFICATION_PLAN" ]] || cert_die "missing certification plan: $CERTIFICATION_PLAN"
  [[ -f "$CERTIFICATION_THRESHOLDS" ]] || cert_die "missing certification thresholds: $CERTIFICATION_THRESHOLDS"
}

require_phase_pass() {
  local phase result
  for phase in "$@"; do
    result="$CERTIFICATION_ROOT/phases/$phase/result.json"
    [[ -f "$result" ]] || cert_die "required prerequisite phase is missing: $phase"
    python3 - "$result" "$phase" "$RELEASE_REFERENCE" "$RELEASE_GIT_COMMIT" "$RELEASE_IMAGE_DIGEST" <<'PYREQ'
import json, pathlib, sys
path, phase, reference, commit, digest = sys.argv[1:]
data = json.loads(pathlib.Path(path).read_text(encoding="utf-8"))
release = data.get("release", {})
if data.get("phase") != phase or data.get("status") != "PASS":
    raise SystemExit(f"prerequisite {phase} is not PASS")
if release.get("reference") != reference or release.get("gitCommit") != commit or release.get("imageDigest") != digest:
    raise SystemExit(f"prerequisite {phase} release identity mismatch")
PYREQ
  done
}

phase_begin() {
  PHASE_ID="$1"; PHASE_NAME="$2"
  [[ "$PHASE_ID" =~ ^54[A-J]$ ]] || cert_die "invalid phase id: $PHASE_ID"
  PHASE_DIR="$CERTIFICATION_ROOT/phases/$PHASE_ID"
  if [[ -f "$PHASE_DIR/result.json" ]]; then
    [[ "${CERTIFICATION_RERUN_CONFIRMATION:-}" == I_UNDERSTAND_THIS_ARCHIVES_THE_PREVIOUS_ATTEMPT ]] || cert_die "phase $PHASE_ID already has evidence; set CERTIFICATION_RERUN_CONFIRMATION to archive and rerun"
    archive="$CERTIFICATION_ROOT/attempts/${PHASE_ID}-$(date -u +%Y%m%dT%H%M%SZ)"
    mkdir -p "$(dirname "$archive")"
    mv "$PHASE_DIR" "$archive"
  fi
  PHASE_LOG_DIR="$PHASE_DIR/logs"
  PHASE_CHECKS_FILE="$PHASE_DIR/checks.jsonl"
  PHASE_STARTED_AT="$(cert_now)"
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
  [[ "$id" =~ ^[a-z0-9][a-z0-9._-]*$ ]] || cert_die "invalid check id: $id"
  local log="$PHASE_LOG_DIR/$id.log" rc status
  printf '[%s] START %s\n' "$(cert_now)" "$id" > "$log"
  set +e
  "$@" >> "$log" 2>&1
  rc=$?
  set -e
  if (( rc == 0 )); then status=PASS; else status=FAIL; fi
  printf '[%s] %s %s exit=%s\n' "$(cert_now)" "$status" "$id" "$rc" >> "$log"
  record_check "$id" "$status" "$rc" "phases/$PHASE_ID/logs/$id.log"
  return "$rc"
}

write_phase_result() {
  local forced_status="${1:-}"
  local ended="$(cert_now)"
  python3 - "$PHASE_DIR/result.json" "$PHASE_CHECKS_FILE" "$PHASE_ID" "$PHASE_NAME" \
    "$PHASE_STARTED_AT" "$ended" "${forced_status}" "$RELEASE_REFERENCE" "$RELEASE_GIT_COMMIT" \
    "$RELEASE_IMAGE_DIGEST" "$CERTIFICATION_ENVIRONMENT" <<'PY'
import json, pathlib, sys
(out, checks_file, phase, name, started, ended, forced, reference, commit, digest, environment) = sys.argv[1:]
checks = [json.loads(line) for line in pathlib.Path(checks_file).read_text(encoding="utf-8").splitlines() if line.strip()]
status = forced or ("PASS" if checks and all(c["status"] == "PASS" for c in checks) else "FAIL")
doc = {
  "schemaVersion": 1,
  "phase": phase,
  "name": name,
  "status": status,
  "startedAt": started,
  "endedAt": ended,
  "release": {"reference": reference, "gitCommit": commit, "imageDigest": digest, "environment": environment},
  "checks": checks,
}
pathlib.Path(out).write_text(json.dumps(doc, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY
  python3 - "$PHASE_DIR/result.json" <<'PY'
import json, sys
raise SystemExit(0 if json.load(open(sys.argv[1], encoding="utf-8"))["status"] == "PASS" else 1)
PY
}

copy_if_present() {
  local src="$1" dst="$2"
  if [[ -f "$src" ]]; then mkdir -p "$(dirname "$dst")"; cp "$src" "$dst"; fi
}

cert_sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | awk '{print $1}'; else shasum -a 256 "$1" | awk '{print $1}'; fi
}
