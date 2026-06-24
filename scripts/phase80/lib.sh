#!/usr/bin/env bash
set -Eeuo pipefail
PHASE80_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PHASE80_MODE="${PHASE80_MODE:-preflight}"
PHASE80_RUN_ID="${PHASE80_RUN_ID:-phase80-$(date -u +%Y%m%dT%H%M%SZ)}"
[[ "$PHASE80_RUN_ID" =~ ^[A-Za-z0-9._-]+$ ]] || { echo 'unsafe PHASE80_RUN_ID' >&2; exit 2; }
PHASE80_EVIDENCE_ROOT="${PHASE80_EVIDENCE_ROOT:-$PHASE80_ROOT/evidence/phase80/$PHASE80_RUN_ID}"
mkdir -p "$PHASE80_EVIDENCE_ROOT/results" "$PHASE80_EVIDENCE_ROOT/logs" "$PHASE80_EVIDENCE_ROOT/artifacts"
phase80_init(){ PHASE80_STEP="$1"; export PHASE80_STEP; cd "$PHASE80_ROOT"; }
phase80_log(){ printf '[%s] [%s] %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$PHASE80_STEP" "$*" | tee -a "$PHASE80_EVIDENCE_ROOT/logs/$PHASE80_STEP.log"; }
phase80_full(){ [[ "$PHASE80_MODE" == full ]]; }
phase80_require_env(){ local v; for v in "$@"; do [[ -n "${!v:-}" ]] || { phase80_log "missing env: $v"; return 1; }; done; }
phase80_run(){ local label="$1"; shift; phase80_log "run: $label"; "$@" >>"$PHASE80_EVIDENCE_ROOT/logs/$PHASE80_STEP.log" 2>&1; }
phase80_emit(){
  local status="$1" message="$2" synthetic="${3:-false}"
  STATUS="$status" MESSAGE="$message" SYNTHETIC="$synthetic" python3 - "$PHASE80_EVIDENCE_ROOT/results/$PHASE80_STEP.json" <<'PYJSON'
import json, os, subprocess, sys
from datetime import datetime, timezone
try:
    commit=subprocess.check_output(['git','rev-parse','HEAD'], text=True, stderr=subprocess.DEVNULL).strip()
except Exception:
    commit='unknown'
json.dump({
  'phase':os.environ['PHASE80_STEP'],
  'status':os.environ['STATUS'],
  'message':os.environ['MESSAGE'],
  'mode':os.environ.get('PHASE80_MODE','preflight'),
  'runId':os.environ['PHASE80_RUN_ID'],
  'environment':os.environ.get('PHASE80_ENVIRONMENT','uat'),
  'gitCommit':commit,
  'synthetic':os.environ['SYNTHETIC'].lower()=='true',
  'generatedAt':datetime.now(timezone.utc).isoformat().replace('+00:00','Z')
}, open(sys.argv[1],'w'), indent=2, sort_keys=True)
PYJSON
  phase80_log "$status: $message"
}
export PHASE80_ROOT PHASE80_MODE PHASE80_RUN_ID PHASE80_EVIDENCE_ROOT
