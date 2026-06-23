#!/usr/bin/env bash
set -Eeuo pipefail
PHASE65_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PHASE65_ROOT="$(cd "$PHASE65_SCRIPT_DIR/../.." && pwd)"
PHASE65_RUN_ID="${PHASE65_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
PHASE65_EVIDENCE_ROOT="${PHASE65_EVIDENCE_ROOT:-$PHASE65_ROOT/scripts/phase65/evidence}"
PHASE65_RUN_DIR="$PHASE65_EVIDENCE_ROOT/$PHASE65_RUN_ID"
export PHASE65_ROOT PHASE65_RUN_ID PHASE65_EVIDENCE_ROOT PHASE65_RUN_DIR PYTHONDONTWRITEBYTECODE=1
mkdir -p "$PHASE65_RUN_DIR"
phase_setup(){ PHASE65_PHASE="$1"; PHASE65_NAME="$2"; PHASE65_DIR="$PHASE65_RUN_DIR/$1"; PHASE65_LOG="$PHASE65_DIR/phase.log"; PHASE65_STARTED="$(date -u +%Y-%m-%dT%H:%M:%SZ)"; mkdir -p "$PHASE65_DIR"; export PHASE65_PHASE PHASE65_NAME PHASE65_DIR PHASE65_LOG PHASE65_STARTED; printf '[%s] %s\n' "$1" "$2" | tee "$PHASE65_LOG"; }
phase_log(){ printf '[%s] %s\n' "${PHASE65_PHASE:-65}" "$*" | tee -a "${PHASE65_LOG:-/dev/stderr}"; }
phase_run(){ local label="$1"; shift; phase_log "RUN $label"; { printf '+ '; printf '%q ' "$@"; printf '\n'; "$@"; } 2>&1 | tee -a "$PHASE65_LOG"; }
phase_preflight(){ [[ "${PHASE65_MODE:-preflight}" == preflight ]]; }
require_uat(){ [[ "${TARGET_ENVIRONMENT:-}" == uat ]] || { phase_log 'TARGET_ENVIRONMENT=uat required'; return 64; }; [[ "${PHASE65_EXECUTE_UAT:-false}" == true ]] || { phase_log 'PHASE65_EXECUTE_UAT=true required'; return 64; }; }
require_operator(){ [[ "${PHASE65_EXECUTE_OPERATOR_ACTIONS:-false}" == true ]] || { phase_log 'PHASE65_EXECUTE_OPERATOR_ACTIONS=true required'; return 64; }; }
phase_finalize(){ local status="$1" code="$2" message="$3"; PHASE65_STATUS="$status" PHASE65_CODE="$code" PHASE65_MESSAGE="$message" python3 - <<'PY'
import json, os, pathlib, subprocess
try: commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=os.environ['PHASE65_ROOT'],text=True,timeout=5).strip()
except Exception: commit='unknown'
p={"phase":os.environ['PHASE65_PHASE'],"name":os.environ['PHASE65_NAME'],"status":os.environ['PHASE65_STATUS'],"exitCode":int(os.environ['PHASE65_CODE']),"message":os.environ['PHASE65_MESSAGE'],"startedAt":os.environ['PHASE65_STARTED'],"finishedAt":__import__('datetime').datetime.now(__import__('datetime').timezone.utc).isoformat().replace('+00:00','Z'),"commit":commit,"applicationImageDigest":os.getenv('APPLICATION_IMAGE_DIGEST',''),"migrationImageDigest":os.getenv('MIGRATION_IMAGE_DIGEST','')}
out=pathlib.Path(os.environ['PHASE65_DIR'])/'result.json'; out.write_text(json.dumps(p,indent=2)+'\n')
PY
phase_log "RESULT $status — $message"; }
