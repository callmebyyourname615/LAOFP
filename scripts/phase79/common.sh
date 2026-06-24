#!/usr/bin/env bash
set -Eeuo pipefail
PHASE79_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; PHASE79_ROOT="$(cd "$PHASE79_SCRIPT_DIR/../.." && pwd)"
PHASE79_RUN_ID="${PHASE79_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"; PHASE79_EVIDENCE_ROOT="${PHASE79_EVIDENCE_ROOT:-$PHASE79_ROOT/scripts/phase79/evidence}"; PHASE79_RUN_DIR="$PHASE79_EVIDENCE_ROOT/$PHASE79_RUN_ID"
export PHASE79_ROOT PHASE79_RUN_ID PHASE79_EVIDENCE_ROOT PHASE79_RUN_DIR PYTHONDONTWRITEBYTECODE=1; mkdir -p "$PHASE79_RUN_DIR"
phase_setup(){ PHASE79_PHASE="$1"; PHASE79_NAME="$2"; PHASE79_DIR="$PHASE79_RUN_DIR/$1"; PHASE79_LOG="$PHASE79_DIR/phase.log"; PHASE79_STARTED="$(date -u +%Y-%m-%dT%H:%M:%SZ)"; mkdir -p "$PHASE79_DIR"; export PHASE79_PHASE PHASE79_NAME PHASE79_DIR PHASE79_LOG PHASE79_STARTED; printf '[%s] %s\n' "$1" "$2" | tee "$PHASE79_LOG"; }
phase_log(){ printf '[%s] %s\n' "${PHASE79_PHASE:-79}" "$*" | tee -a "${PHASE79_LOG:-/dev/stderr}"; }
phase_run(){ local label="$1"; shift; phase_log "RUN $label"; { printf '+ '; printf '%q ' "$@"; printf '\n'; "$@"; } 2>&1 | tee -a "$PHASE79_LOG"; }
phase_preflight(){ [[ "${PHASE79_MODE:-preflight}" == preflight ]]; }; phase_repo(){ [[ "${PHASE79_MODE:-preflight}" == repo ]]; }
require_prod(){ [[ "${TARGET_ENVIRONMENT:-}" == production ]] || { phase_log 'TARGET_ENVIRONMENT=production required'; return 64; }; [[ "${PHASE79_EXECUTE_PRODUCTION:-false}" == true ]] || { phase_log 'PHASE79_EXECUTE_PRODUCTION=true required'; return 64; }; }
require_flag(){ local n="$1"; [[ "${!n:-false}" == true ]] || { phase_log "$n=true required"; return 64; }; }
require_identity(){ [[ "${PHASE79_COMMIT:-}" =~ ^[a-f0-9]{40}$ ]] || { phase_log 'PHASE79_COMMIT invalid'; return 64; }; [[ "${APPLICATION_IMAGE_DIGEST:-}" =~ ^sha256:[a-f0-9]{64}$ ]] || { phase_log 'APPLICATION_IMAGE_DIGEST invalid'; return 64; }; [[ "${MIGRATION_IMAGE_DIGEST:-}" =~ ^sha256:[a-f0-9]{64}$ ]] || { phase_log 'MIGRATION_IMAGE_DIGEST invalid'; return 64; }; }
phase_finalize(){ local status="$1" code="$2" message="$3"; PHASE79_STATUS="$status" PHASE79_CODE="$code" PHASE79_MESSAGE="$message" python3 - <<'PY2'
import datetime,hashlib,json,os,pathlib
p={'schemaVersion':1,'phase':os.environ['PHASE79_PHASE'],'name':os.environ['PHASE79_NAME'],'status':os.environ['PHASE79_STATUS'],'exitCode':int(os.environ['PHASE79_CODE']),'message':os.environ['PHASE79_MESSAGE'],'startedAt':os.environ['PHASE79_STARTED'],'finishedAt':datetime.datetime.now(datetime.timezone.utc).isoformat().replace('+00:00','Z'),'commit':os.getenv('PHASE79_COMMIT','unknown'),'applicationImageDigest':os.getenv('APPLICATION_IMAGE_DIGEST',''),'migrationImageDigest':os.getenv('MIGRATION_IMAGE_DIGEST',''),'targetEnvironment':os.getenv('TARGET_ENVIRONMENT','')}
out=pathlib.Path(os.environ['PHASE79_DIR'])/'result.json'; out.write_text(json.dumps(p,indent=2,sort_keys=True)+'\n'); (out.parent/'result.json.sha256').write_text(f"{hashlib.sha256(out.read_bytes()).hexdigest()}  result.json\n")
PY2
phase_log "RESULT $status — $message"; }
