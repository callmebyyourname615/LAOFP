#!/usr/bin/env bash
set -Eeuo pipefail
PHASE75_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PHASE75_ROOT="$(cd "$PHASE75_SCRIPT_DIR/../.." && pwd)"
PHASE75_RUN_ID="${PHASE75_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
PHASE75_EVIDENCE_ROOT="${PHASE75_EVIDENCE_ROOT:-$PHASE75_ROOT/scripts/phase75/evidence}"
PHASE75_RUN_DIR="$PHASE75_EVIDENCE_ROOT/$PHASE75_RUN_ID"
export PHASE75_ROOT PHASE75_RUN_ID PHASE75_EVIDENCE_ROOT PHASE75_RUN_DIR PYTHONDONTWRITEBYTECODE=1
mkdir -p "$PHASE75_RUN_DIR"
phase_setup(){ PHASE75_PHASE="$1"; PHASE75_NAME="$2"; PHASE75_DIR="$PHASE75_RUN_DIR/$1"; PHASE75_LOG="$PHASE75_DIR/phase.log"; PHASE75_STARTED="$(date -u +%Y-%m-%dT%H:%M:%SZ)"; mkdir -p "$PHASE75_DIR"; export PHASE75_PHASE PHASE75_NAME PHASE75_DIR PHASE75_LOG PHASE75_STARTED; printf '[%s] %s\n' "$1" "$2" | tee "$PHASE75_LOG"; }
phase_log(){ printf '[%s] %s\n' "${PHASE75_PHASE:-75}" "$*" | tee -a "${PHASE75_LOG:-/dev/stderr}"; }
phase_run(){ local label="$1"; shift; phase_log "RUN $label"; { printf '+ '; printf '%q ' "$@"; printf '\n'; "$@"; } 2>&1 | tee -a "$PHASE75_LOG"; }
phase_preflight(){ [[ "${PHASE75_MODE:-preflight}" == preflight ]]; }
phase_repo(){ [[ "${PHASE75_MODE:-preflight}" == repo ]]; }
require_prod(){ [[ "${TARGET_ENVIRONMENT:-}" == production ]] || { phase_log 'TARGET_ENVIRONMENT=production required'; return 64; }; [[ "${PHASE75_EXECUTE_PRODUCTION:-false}" == true ]] || { phase_log 'PHASE75_EXECUTE_PRODUCTION=true required'; return 64; }; }
require_uat(){ [[ "${TARGET_ENVIRONMENT:-}" == uat ]] || { phase_log 'TARGET_ENVIRONMENT=uat required'; return 64; }; [[ "${PHASE75_EXECUTE_UAT:-false}" == true ]] || { phase_log 'PHASE75_EXECUTE_UAT=true required'; return 64; }; }
require_flag(){ local name="$1"; [[ "${!name:-false}" == true ]] || { phase_log "$name=true required"; return 64; }; }
require_file(){ [[ -f "$1" ]] || { phase_log "required file missing: $1"; return 66; }; }
require_identity(){ [[ "${PHASE75_COMMIT:-}" =~ ^[a-f0-9]{40}$ ]] || { phase_log 'PHASE75_COMMIT invalid'; return 64; }; [[ "${APPLICATION_IMAGE_DIGEST:-}" =~ ^sha256:[a-f0-9]{64}$ ]] || { phase_log 'APPLICATION_IMAGE_DIGEST invalid'; return 64; }; [[ "${MIGRATION_IMAGE_DIGEST:-}" =~ ^sha256:[a-f0-9]{64}$ ]] || { phase_log 'MIGRATION_IMAGE_DIGEST invalid'; return 64; }; }
phase_finalize(){ local status="$1" code="$2" message="$3"; PHASE75_STATUS="$status" PHASE75_CODE="$code" PHASE75_MESSAGE="$message" python3 - <<'PY'
import datetime, hashlib, json, os, pathlib
p={'schemaVersion':1,'phase':os.environ['PHASE75_PHASE'],'name':os.environ['PHASE75_NAME'],'status':os.environ['PHASE75_STATUS'],'exitCode':int(os.environ['PHASE75_CODE']),'message':os.environ['PHASE75_MESSAGE'],'startedAt':os.environ['PHASE75_STARTED'],'finishedAt':datetime.datetime.now(datetime.timezone.utc).isoformat().replace('+00:00','Z'),'commit':os.getenv('PHASE75_COMMIT','unknown'),'applicationImageDigest':os.getenv('APPLICATION_IMAGE_DIGEST',''),'migrationImageDigest':os.getenv('MIGRATION_IMAGE_DIGEST',''),'targetEnvironment':os.getenv('TARGET_ENVIRONMENT','')}
out=pathlib.Path(os.environ['PHASE75_DIR'])/'result.json'; out.write_text(json.dumps(p,indent=2,sort_keys=True)+'\n'); (out.parent/'result.json.sha256').write_text(f"{hashlib.sha256(out.read_bytes()).hexdigest()}  result.json\n")
PY
phase_log "RESULT $status — $message"; }
