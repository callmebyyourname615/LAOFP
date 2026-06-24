#!/usr/bin/env bash
set -Eeuo pipefail
PHASE78_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PHASE78_ROOT="$(cd "$PHASE78_SCRIPT_DIR/../.." && pwd)"
PHASE78_RUN_ID="${PHASE78_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
PHASE78_EVIDENCE_ROOT="${PHASE78_EVIDENCE_ROOT:-$PHASE78_ROOT/scripts/phase78/evidence}"
PHASE78_RUN_DIR="$PHASE78_EVIDENCE_ROOT/$PHASE78_RUN_ID"
export PHASE78_ROOT PHASE78_RUN_ID PHASE78_EVIDENCE_ROOT PHASE78_RUN_DIR PYTHONDONTWRITEBYTECODE=1
mkdir -p "$PHASE78_RUN_DIR"
phase_setup(){ PHASE78_PHASE="$1"; PHASE78_NAME="$2"; PHASE78_DIR="$PHASE78_RUN_DIR/$1"; PHASE78_LOG="$PHASE78_DIR/phase.log"; PHASE78_STARTED="$(date -u +%Y-%m-%dT%H:%M:%SZ)"; mkdir -p "$PHASE78_DIR"; export PHASE78_PHASE PHASE78_NAME PHASE78_DIR PHASE78_LOG PHASE78_STARTED; printf '[%s] %s\n' "$1" "$2" | tee "$PHASE78_LOG"; }
phase_log(){ printf '[%s] %s\n' "${PHASE78_PHASE:-78}" "$*" | tee -a "${PHASE78_LOG:-/dev/stderr}"; }
phase_run(){ local label="$1"; shift; phase_log "RUN $label"; { printf '+ '; printf '%q ' "$@"; printf '\n'; "$@"; } 2>&1 | tee -a "$PHASE78_LOG"; }
phase_preflight(){ [[ "${PHASE78_MODE:-preflight}" == preflight ]]; }
phase_repo(){ [[ "${PHASE78_MODE:-preflight}" == repo ]]; }
require_uat(){ [[ "${TARGET_ENVIRONMENT:-}" == uat ]] || { phase_log 'TARGET_ENVIRONMENT=uat required'; return 64; }; [[ "${PHASE78_EXECUTE_UAT:-false}" == true ]] || { phase_log 'PHASE78_EXECUTE_UAT=true required'; return 64; }; }
require_flag(){ local name="$1"; [[ "${!name:-false}" == true ]] || { phase_log "$name=true required"; return 64; }; }
require_identity(){ [[ "${PHASE78_COMMIT:-}" =~ ^[a-f0-9]{40}$ ]] || { phase_log 'PHASE78_COMMIT must be full lowercase git SHA'; return 64; }; [[ "${APPLICATION_IMAGE_DIGEST:-}" =~ ^sha256:[a-f0-9]{64}$ ]] || { phase_log 'APPLICATION_IMAGE_DIGEST invalid'; return 64; }; [[ "${MIGRATION_IMAGE_DIGEST:-}" =~ ^sha256:[a-f0-9]{64}$ ]] || { phase_log 'MIGRATION_IMAGE_DIGEST invalid'; return 64; }; }
phase_finalize(){ local status="$1" code="$2" message="$3"; PHASE78_STATUS="$status" PHASE78_CODE="$code" PHASE78_MESSAGE="$message" python3 - <<'PY2'
import datetime, hashlib, json, os, pathlib
p={'schemaVersion':1,'phase':os.environ['PHASE78_PHASE'],'name':os.environ['PHASE78_NAME'],'status':os.environ['PHASE78_STATUS'],'exitCode':int(os.environ['PHASE78_CODE']),'message':os.environ['PHASE78_MESSAGE'],'startedAt':os.environ['PHASE78_STARTED'],'finishedAt':datetime.datetime.now(datetime.timezone.utc).isoformat().replace('+00:00','Z'),'commit':os.getenv('PHASE78_COMMIT','unknown'),'applicationImageDigest':os.getenv('APPLICATION_IMAGE_DIGEST',''),'migrationImageDigest':os.getenv('MIGRATION_IMAGE_DIGEST',''),'targetEnvironment':os.getenv('TARGET_ENVIRONMENT','')}
out=pathlib.Path(os.environ['PHASE78_DIR'])/'result.json'; out.write_text(json.dumps(p,indent=2,sort_keys=True)+'\n')
(out.parent/'result.json.sha256').write_text(f"{hashlib.sha256(out.read_bytes()).hexdigest()}  result.json\n")
PY2
phase_log "RESULT $status — $message"; }
