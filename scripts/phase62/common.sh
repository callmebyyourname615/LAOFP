#!/usr/bin/env bash
set -Eeuo pipefail
PHASE62_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PHASE62_ROOT="$(cd "$PHASE62_SCRIPT_DIR/../.." && pwd)"
PHASE62_RUN_ID="${PHASE62_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
PHASE62_EVIDENCE_ROOT="${PHASE62_EVIDENCE_ROOT:-$PHASE62_ROOT/scripts/phase62/evidence}"
PHASE62_RUN_DIR="$PHASE62_EVIDENCE_ROOT/$PHASE62_RUN_ID"
export PHASE62_ROOT PHASE62_RUN_ID PHASE62_EVIDENCE_ROOT PHASE62_RUN_DIR
mkdir -p "$PHASE62_RUN_DIR"
export PYTHONDONTWRITEBYTECODE=1

phase_setup() {
  PHASE62_PHASE="$1"; PHASE62_PHASE_NAME="$2"
  PHASE62_PHASE_DIR="$PHASE62_RUN_DIR/$PHASE62_PHASE"
  PHASE62_PHASE_LOG="$PHASE62_PHASE_DIR/phase.log"
  PHASE62_PHASE_STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  mkdir -p "$PHASE62_PHASE_DIR"
  export PHASE62_PHASE PHASE62_PHASE_NAME PHASE62_PHASE_DIR PHASE62_PHASE_LOG PHASE62_PHASE_STARTED_AT
  printf '[%s] %s — %s\n' "$PHASE62_PHASE" "$PHASE62_PHASE_NAME" "$PHASE62_PHASE_STARTED_AT" | tee "$PHASE62_PHASE_LOG"
}
phase_log() { printf '[%s] %s\n' "${PHASE62_PHASE:-phase62}" "$*" | tee -a "${PHASE62_PHASE_LOG:-/dev/stderr}"; }
phase_run() { local label="$1"; shift; phase_log "RUN $label"; { printf '+ '; printf '%q ' "$@"; printf '\n'; "$@"; } 2>&1 | tee -a "$PHASE62_PHASE_LOG"; }
phase_is_preflight() { [[ "${PHASE62_PREFLIGHT_ONLY:-false}" == "true" ]]; }
phase_require_uat() {
  [[ "${TARGET_ENVIRONMENT:-}" == "uat" ]] || { phase_log 'ERROR TARGET_ENVIRONMENT=uat is required'; return 64; }
  [[ "${PHASE62_EXECUTE_RUNTIME:-}" == "true" ]] || { phase_log 'ERROR PHASE62_EXECUTE_RUNTIME=true is required'; return 64; }
}
phase_finalize() {
  local status="$1" code="$2" message="${3:-}" finished
  finished="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  phase_log "RESULT $status — $message"
  PHASE62_RESULT_PATH="$PHASE62_PHASE_DIR/result.json" \
  PHASE62_RESULT_STATUS="$status" \
  PHASE62_RESULT_CODE="$code" \
  PHASE62_RESULT_MESSAGE="$message" \
  PHASE62_RESULT_FINISHED_AT="$finished" \
  python3 - <<'PY_RESULT'
import json
import os
import pathlib
import subprocess

out = pathlib.Path(os.environ["PHASE62_RESULT_PATH"])
try:
    commit = subprocess.check_output(
        ["git", "rev-parse", "HEAD"],
        cwd=os.environ["PHASE62_ROOT"],
        text=True,
        timeout=5,
    ).strip()
except Exception:
    commit = "unknown"

payload = {
    "phase": os.environ["PHASE62_PHASE"],
    "name": os.environ["PHASE62_PHASE_NAME"],
    "status": os.environ["PHASE62_RESULT_STATUS"],
    "exitCode": int(os.environ["PHASE62_RESULT_CODE"]),
    "message": os.environ["PHASE62_RESULT_MESSAGE"],
    "startedAt": os.environ["PHASE62_PHASE_STARTED_AT"],
    "finishedAt": os.environ["PHASE62_RESULT_FINISHED_AT"],
    "commit": commit,
}
with out.open("w", encoding="utf-8") as handle:
    json.dump(payload, handle, indent=2, ensure_ascii=False)
    handle.write("\n")
PY_RESULT
}
