#!/usr/bin/env bash
set -Eeuo pipefail
PHASE74_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PHASE74_ROOT="$(cd "$PHASE74_SCRIPT_DIR/../.." && pwd)"
PHASE74_RUN_ID="${PHASE74_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
PHASE74_EVIDENCE_ROOT="${PHASE74_EVIDENCE_ROOT:-$PHASE74_ROOT/scripts/phase74/evidence}"
PHASE74_RUN_DIR="$PHASE74_EVIDENCE_ROOT/$PHASE74_RUN_ID"
export PHASE74_ROOT PHASE74_RUN_ID PHASE74_EVIDENCE_ROOT PHASE74_RUN_DIR PYTHONDONTWRITEBYTECODE=1
mkdir -p "$PHASE74_RUN_DIR"
phase_setup(){ PHASE74_PHASE="$1"; PHASE74_NAME="$2"; PHASE74_DIR="$PHASE74_RUN_DIR/$1"; PHASE74_LOG="$PHASE74_DIR/phase.log"; PHASE74_STARTED="$(date -u +%Y-%m-%dT%H:%M:%SZ)"; mkdir -p "$PHASE74_DIR"; export PHASE74_PHASE PHASE74_NAME PHASE74_DIR PHASE74_LOG PHASE74_STARTED; printf '[%s] %s\n' "$1" "$2" | tee "$PHASE74_LOG"; }
phase_log(){ printf '[%s] %s\n' "${PHASE74_PHASE:-74}" "$*" | tee -a "${PHASE74_LOG:-/dev/stderr}"; }
phase_run(){ local label="$1"; shift; phase_log "RUN $label"; { printf '+ '; printf '%q ' "$@"; printf '\n'; "$@"; } 2>&1 | tee -a "$PHASE74_LOG"; }
phase_preflight(){ [[ "${PHASE74_MODE:-preflight}" == preflight ]]; }
phase_repo(){ [[ "${PHASE74_MODE:-preflight}" == repo ]]; }
require_uat(){ [[ "${TARGET_ENVIRONMENT:-}" == uat ]] || { phase_log 'TARGET_ENVIRONMENT=uat required'; return 64; }; [[ "${PHASE74_EXECUTE_UAT:-false}" == true ]] || { phase_log 'PHASE74_EXECUTE_UAT=true required'; return 64; }; }
require_flag(){ local name="$1"; [[ "${!name:-false}" == true ]] || { phase_log "$name=true required"; return 64; }; }
require_file(){ [[ -f "$1" ]] || { phase_log "required file missing: $1"; return 66; }; }
require_identity(){ [[ "${PHASE74_COMMIT:-}" =~ ^[a-f0-9]{40}$ ]] || { phase_log 'PHASE74_COMMIT must be full lowercase git SHA'; return 64; }; [[ "${APPLICATION_IMAGE_DIGEST:-}" =~ ^sha256:[a-f0-9]{64}$ ]] || { phase_log 'APPLICATION_IMAGE_DIGEST invalid'; return 64; }; [[ "${MIGRATION_IMAGE_DIGEST:-}" =~ ^sha256:[a-f0-9]{64}$ ]] || { phase_log 'MIGRATION_IMAGE_DIGEST invalid'; return 64; }; }
phase_finalize(){ local status="$1" code="$2" message="$3"; PHASE74_STATUS="$status" PHASE74_CODE="$code" PHASE74_MESSAGE="$message" python3 - <<'PY'
import datetime, hashlib, json, os, pathlib
p={
 'schemaVersion':1,'phase':os.environ['PHASE74_PHASE'],'name':os.environ['PHASE74_NAME'],
 'status':os.environ['PHASE74_STATUS'],'exitCode':int(os.environ['PHASE74_CODE']),
 'message':os.environ['PHASE74_MESSAGE'],'startedAt':os.environ['PHASE74_STARTED'],
 'finishedAt':datetime.datetime.now(datetime.timezone.utc).isoformat().replace('+00:00','Z'),
 'commit':os.getenv('PHASE74_COMMIT','unknown'),'applicationImageDigest':os.getenv('APPLICATION_IMAGE_DIGEST',''),
 'migrationImageDigest':os.getenv('MIGRATION_IMAGE_DIGEST',''),'targetEnvironment':os.getenv('TARGET_ENVIRONMENT','')}
out=pathlib.Path(os.environ['PHASE74_DIR'])/'result.json'; out.write_text(json.dumps(p,indent=2,sort_keys=True)+'\n')
sha=hashlib.sha256(out.read_bytes()).hexdigest(); (out.parent/'result.json.sha256').write_text(f'{sha}  result.json\n')
PY
phase_log "RESULT $status — $message"; }
