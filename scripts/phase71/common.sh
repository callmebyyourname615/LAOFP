#!/usr/bin/env bash
set -Eeuo pipefail
PHASE71_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PHASE71_ROOT="$(cd "$PHASE71_SCRIPT_DIR/../.." && pwd)"
PHASE71_RUN_ID="${PHASE71_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
PHASE71_EVIDENCE_ROOT="${PHASE71_EVIDENCE_ROOT:-$PHASE71_ROOT/scripts/phase71/evidence}"
PHASE71_RUN_DIR="$PHASE71_EVIDENCE_ROOT/$PHASE71_RUN_ID"
export PHASE71_ROOT PHASE71_RUN_ID PHASE71_EVIDENCE_ROOT PHASE71_RUN_DIR PYTHONDONTWRITEBYTECODE=1
mkdir -p "$PHASE71_RUN_DIR"
phase_setup(){ PHASE71_PHASE="$1"; PHASE71_NAME="$2"; PHASE71_DIR="$PHASE71_RUN_DIR/$1"; PHASE71_LOG="$PHASE71_DIR/phase.log"; PHASE71_STARTED="$(date -u +%Y-%m-%dT%H:%M:%SZ)"; mkdir -p "$PHASE71_DIR"; export PHASE71_PHASE PHASE71_NAME PHASE71_DIR PHASE71_LOG PHASE71_STARTED; printf '[%s] %s\n' "$1" "$2" | tee "$PHASE71_LOG"; }
phase_log(){ printf '[%s] %s\n' "${PHASE71_PHASE:-71}" "$*" | tee -a "${PHASE71_LOG:-/dev/stderr}"; }
phase_run(){ local label="$1"; shift; phase_log "RUN $label"; { printf '+ '; printf '%q ' "$@"; printf '\n'; "$@"; } 2>&1 | tee -a "$PHASE71_LOG"; }
phase_preflight(){ [[ "${PHASE71_MODE:-preflight}" == preflight ]]; }
phase_repo(){ [[ "${PHASE71_MODE:-preflight}" == repo ]]; }
require_uat(){ [[ "${TARGET_ENVIRONMENT:-}" == uat ]] || { phase_log 'TARGET_ENVIRONMENT=uat required'; return 64; }; [[ "${PHASE71_EXECUTE_UAT:-false}" == true ]] || { phase_log 'PHASE71_EXECUTE_UAT=true required'; return 64; }; }
require_flag(){ local name="$1"; [[ "${!name:-false}" == true ]] || { phase_log "$name=true required"; return 64; }; }
require_file(){ [[ -f "$1" ]] || { phase_log "required file missing: $1"; return 66; }; }
phase_finalize(){ local status="$1" code="$2" message="$3"; PHASE71_STATUS="$status" PHASE71_CODE="$code" PHASE71_MESSAGE="$message" python3 - <<'PY'
import datetime, hashlib, json, os, pathlib, subprocess
root=os.environ['PHASE71_ROOT']
commit=os.getenv('PHASE71_COMMIT','unknown')
p={
 'schemaVersion':1,'phase':os.environ['PHASE71_PHASE'],'name':os.environ['PHASE71_NAME'],
 'status':os.environ['PHASE71_STATUS'],'exitCode':int(os.environ['PHASE71_CODE']),
 'message':os.environ['PHASE71_MESSAGE'],'startedAt':os.environ['PHASE71_STARTED'],
 'finishedAt':datetime.datetime.now(datetime.timezone.utc).isoformat().replace('+00:00','Z'),
 'commit':commit,'applicationImageDigest':os.getenv('APPLICATION_IMAGE_DIGEST',''),
 'migrationImageDigest':os.getenv('MIGRATION_IMAGE_DIGEST',''),
 'targetEnvironment':os.getenv('TARGET_ENVIRONMENT','')}
out=pathlib.Path(os.environ['PHASE71_DIR'])/'result.json'; out.write_text(json.dumps(p,indent=2,sort_keys=True)+'\n')
sha=hashlib.sha256(out.read_bytes()).hexdigest(); (out.parent/'result.json.sha256').write_text(f'{sha}  result.json\n')
PY
phase_log "RESULT $status — $message"; }
