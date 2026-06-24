#!/usr/bin/env bash
set -Eeuo pipefail
PHASE81_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PHASE81_MODE="${PHASE81_MODE:-preflight}"
PHASE81_RUN_ID="${PHASE81_RUN_ID:-phase81-$(date -u +%Y%m%dT%H%M%SZ)}"
PHASE81_EVIDENCE_ROOT="${PHASE81_EVIDENCE_ROOT:-$PHASE81_ROOT/evidence/phase81/$PHASE81_RUN_ID}"
mkdir -p "$PHASE81_EVIDENCE_ROOT/results" "$PHASE81_EVIDENCE_ROOT/logs" "$PHASE81_EVIDENCE_ROOT/artifacts"
phase81_init(){ PHASE81_STEP="$1"; export PHASE81_STEP; cd "$PHASE81_ROOT"; }
phase81_full(){ [[ "$PHASE81_MODE" == full ]]; }
phase81_emit(){
  local status="$1" message="$2"
  STATUS="$status" MESSAGE="$message" python3 - "$PHASE81_EVIDENCE_ROOT/results/$PHASE81_STEP.json" <<'PYJSON'
import json,os,subprocess,sys
from datetime import datetime,timezone
try:
    commit=subprocess.check_output(['git','rev-parse','HEAD'],text=True,stderr=subprocess.DEVNULL).strip()
except Exception:
    commit='unknown'
json.dump({
 'phase':os.environ['PHASE81_STEP'],
 'status':os.environ['STATUS'],
 'message':os.environ['MESSAGE'],
 'mode':os.environ.get('PHASE81_MODE','preflight'),
 'runId':os.environ['PHASE81_RUN_ID'],
 'gitCommit':commit,
 'synthetic':False,
 'generatedAt':datetime.now(timezone.utc).isoformat().replace('+00:00','Z')
},open(sys.argv[1],'w'),indent=2,sort_keys=True)
PYJSON
  printf '[%s] %s: %s\n' "$PHASE81_STEP" "$status" "$message" | tee -a "$PHASE81_EVIDENCE_ROOT/logs/$PHASE81_STEP.log"
}
export PHASE81_ROOT PHASE81_MODE PHASE81_RUN_ID PHASE81_EVIDENCE_ROOT
