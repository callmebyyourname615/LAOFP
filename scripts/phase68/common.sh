#!/usr/bin/env bash
set -Eeuo pipefail
PHASE68_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PHASE68_ROOT="$(cd "$PHASE68_SCRIPT_DIR/../.." && pwd)"
PHASE68_RUN_ID="${PHASE68_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
PHASE68_EVIDENCE_ROOT="${PHASE68_EVIDENCE_ROOT:-$PHASE68_ROOT/scripts/phase68/evidence}"
PHASE68_RUN_DIR="$PHASE68_EVIDENCE_ROOT/$PHASE68_RUN_ID"
export PHASE68_ROOT PHASE68_RUN_ID PHASE68_EVIDENCE_ROOT PHASE68_RUN_DIR PYTHONDONTWRITEBYTECODE=1
mkdir -p "$PHASE68_RUN_DIR"
phase_setup(){ PHASE68_PHASE="$1"; PHASE68_NAME="$2"; PHASE68_DIR="$PHASE68_RUN_DIR/$1"; PHASE68_LOG="$PHASE68_DIR/phase.log"; PHASE68_STARTED="$(date -u +%Y-%m-%dT%H:%M:%SZ)"; mkdir -p "$PHASE68_DIR"; export PHASE68_PHASE PHASE68_NAME PHASE68_DIR PHASE68_LOG PHASE68_STARTED; printf '[%s] %s\n' "$1" "$2" | tee "$PHASE68_LOG"; }
phase_log(){ printf '[%s] %s\n' "${PHASE68_PHASE:-68}" "$*" | tee -a "${PHASE68_LOG:-/dev/stderr}"; }
phase_run(){ local label="$1"; shift; phase_log "RUN $label"; { printf '+ '; printf '%q ' "$@"; printf '\n'; "$@"; } 2>&1 | tee -a "$PHASE68_LOG"; }
phase_preflight(){ [[ "${PHASE68_MODE:-preflight}" == preflight ]]; }
phase_repo(){ [[ "${PHASE68_MODE:-preflight}" == repo ]]; }
require_uat(){ [[ "${TARGET_ENVIRONMENT:-}" == uat ]] || { phase_log 'TARGET_ENVIRONMENT=uat required'; return 64; }; [[ "${PHASE68_EXECUTE_UAT:-false}" == true ]] || { phase_log 'PHASE68_EXECUTE_UAT=true required'; return 64; }; }
require_operator(){ [[ "${PHASE68_EXECUTE_OPERATOR_ACTIONS:-false}" == true ]] || { phase_log 'PHASE68_EXECUTE_OPERATOR_ACTIONS=true required'; return 64; }; }
require_file(){ [[ -f "$1" ]] || { phase_log "required file missing: $1"; return 66; }; }
phase_finalize(){ local status="$1" code="$2" message="$3"; PHASE68_STATUS="$status" PHASE68_CODE="$code" PHASE68_MESSAGE="$message" python3 - <<'PY'
import json, os, pathlib, subprocess, hashlib
root=os.environ['PHASE68_ROOT']
try: commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True,timeout=5).strip()
except Exception: commit='unknown'
p={
  'schemaVersion':1,
  'phase':os.environ['PHASE68_PHASE'],
  'name':os.environ['PHASE68_NAME'],
  'status':os.environ['PHASE68_STATUS'],
  'exitCode':int(os.environ['PHASE68_CODE']),
  'message':os.environ['PHASE68_MESSAGE'],
  'startedAt':os.environ['PHASE68_STARTED'],
  'finishedAt':__import__('datetime').datetime.now(__import__('datetime').timezone.utc).isoformat().replace('+00:00','Z'),
  'commit':commit,
  'applicationImageDigest':os.getenv('APPLICATION_IMAGE_DIGEST',''),
  'migrationImageDigest':os.getenv('MIGRATION_IMAGE_DIGEST',''),
  'targetEnvironment':os.getenv('TARGET_ENVIRONMENT','')
}
out=pathlib.Path(os.environ['PHASE68_DIR'])/'result.json'
out.write_text(json.dumps(p,indent=2,sort_keys=True)+'\n')
sha=hashlib.sha256(out.read_bytes()).hexdigest()
(out.parent/'result.json.sha256').write_text(f'{sha}  result.json\n')
PY
phase_log "RESULT $status — $message"; }
