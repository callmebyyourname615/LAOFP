#!/usr/bin/env bash
set -Eeuo pipefail
ENTERPRISE_PLAN="${ENTERPRISE_PLAN:-config/phase57-enterprise-plan.yaml}"
ENTERPRISE_THRESHOLDS="${ENTERPRISE_THRESHOLDS:-config/phase57-thresholds.yaml}"
ENTERPRISE_ROOT="${ENTERPRISE_ROOT:-build/phase57-enterprise}"
enterprise_die(){ printf 'ERROR: %s\n' "$*" >&2; exit 1; }
enterprise_now(){ date -u +%Y-%m-%dT%H:%M:%SZ; }
enterprise_require_command(){ command -v "$1" >/dev/null 2>&1 || enterprise_die "required command not found: $1"; }
enterprise_require_identity(){
  : "${ENTERPRISE_ENVIRONMENT:?ENTERPRISE_ENVIRONMENT is required}"
  : "${RELEASE_REFERENCE:?RELEASE_REFERENCE is required}"
  : "${RELEASE_GIT_COMMIT:?RELEASE_GIT_COMMIT is required}"
  : "${RELEASE_IMAGE_DIGEST:?RELEASE_IMAGE_DIGEST is required}"
  case "$ENTERPRISE_ENVIRONMENT" in production|operations|security|compliance|dr|financial-control) ;; *) enterprise_die "unsupported ENTERPRISE_ENVIRONMENT: $ENTERPRISE_ENVIRONMENT";; esac
  [[ "$RELEASE_REFERENCE" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{2,127}$ ]] || enterprise_die "invalid RELEASE_REFERENCE"
  [[ "$RELEASE_GIT_COMMIT" =~ ^[a-f0-9]{40}$ ]] || enterprise_die "RELEASE_GIT_COMMIT must be a full lowercase SHA"
  [[ "$RELEASE_IMAGE_DIGEST" =~ ^sha256:[a-f0-9]{64}$ ]] || enterprise_die "invalid RELEASE_IMAGE_DIGEST"
  [[ -f "$ENTERPRISE_PLAN" && -f "$ENTERPRISE_THRESHOLDS" ]] || enterprise_die "Phase 57 plan or thresholds missing"
}
enterprise_require_environment(){ local ok=false; for e in "$@"; do [[ "$ENTERPRISE_ENVIRONMENT" == "$e" ]] && ok=true; done; $ok || enterprise_die "$PHASE_ID cannot run in $ENTERPRISE_ENVIRONMENT"; }
enterprise_require_confirmation(){ local var="$1" expected="$2" value=""; if [[ -v "$var" ]]; then value="${!var}"; fi; [[ "$value" == "$expected" ]] || enterprise_die "$var confirmation is required"; }
enterprise_require_file(){ [[ -f "$1" ]] || enterprise_die "required file not found: $1"; }
enterprise_require_phase_pass(){
  local phase result
  for phase in "$@"; do
    result="$ENTERPRISE_ROOT/phases/$phase/result.json"
    [[ -f "$result" ]] || enterprise_die "missing prerequisite $phase"
    python3 - "$result" "$phase" "$RELEASE_REFERENCE" "$RELEASE_GIT_COMMIT" "$RELEASE_IMAGE_DIGEST" <<'PY'
import json,sys
p,phase,ref,commit,digest=sys.argv[1:]
d=json.load(open(p,encoding='utf-8')); r=d.get('release',{})
if d.get('phase')!=phase or d.get('status')!='PASS': raise SystemExit(f'{phase} is not PASS')
if (r.get('reference'),r.get('gitCommit'),r.get('imageDigest'))!=(ref,commit,digest): raise SystemExit(f'{phase} release identity mismatch')
PY
  done
}
enterprise_phase_begin(){
  PHASE_ID="$1"; PHASE_NAME="$2"; [[ "$PHASE_ID" =~ ^57[A-J]$ ]] || enterprise_die "invalid phase id"
  PHASE_DIR="$ENTERPRISE_ROOT/phases/$PHASE_ID"
  if [[ -d "$PHASE_DIR" ]] && find "$PHASE_DIR" -mindepth 1 -print -quit | grep -q .; then
    [[ "${ENTERPRISE_RERUN_CONFIRMATION:-}" == I_UNDERSTAND_THIS_ARCHIVES_THE_PREVIOUS_ATTEMPT ]] || enterprise_die "$PHASE_ID evidence exists; rerun confirmation required"
    archive="$ENTERPRISE_ROOT/attempts/${PHASE_ID}-$(date -u +%Y%m%dT%H%M%SZ)-$$"; mkdir -p "$(dirname "$archive")"; mv "$PHASE_DIR" "$archive"
  fi
  PHASE_LOG_DIR="$PHASE_DIR/logs"; PHASE_CHECKS_FILE="$PHASE_DIR/checks.jsonl"; PHASE_STARTED_AT="$(enterprise_now)"
  mkdir -p "$PHASE_LOG_DIR"; : > "$PHASE_CHECKS_FILE"
  export PHASE_ID PHASE_NAME PHASE_DIR PHASE_LOG_DIR PHASE_CHECKS_FILE PHASE_STARTED_AT
}
enterprise_record_check(){ python3 - "$PHASE_CHECKS_FILE" "$1" "$2" "$3" "$4" <<'PY'
import json,pathlib,sys
p,i,s,c,l=sys.argv[1:]
with pathlib.Path(p).open('a',encoding='utf-8') as f: f.write(json.dumps({'id':i,'status':s,'exitCode':int(c),'log':l},sort_keys=True)+'\n')
PY
}
enterprise_run_check(){
  local id="$1"; shift; [[ "$id" =~ ^[a-z0-9][a-z0-9._-]*$ ]] || enterprise_die "invalid check id"
  local log="$PHASE_LOG_DIR/$id.log" rc status
  printf '[%s] START %s\n' "$(enterprise_now)" "$id" > "$log"
  set +e; "$@" >> "$log" 2>&1; rc=$?; set -e
  [[ $rc -eq 0 ]] && status=PASS || status=FAIL
  printf '[%s] %s exit=%s\n' "$(enterprise_now)" "$status" "$rc" >> "$log"
  enterprise_record_check "$id" "$status" "$rc" "phases/$PHASE_ID/logs/$id.log"
  return "$rc"
}
enterprise_write_result(){
  local forced="${1:-}" ended; ended="$(enterprise_now)"
  python3 - "$PHASE_DIR/result.json" "$PHASE_CHECKS_FILE" "$PHASE_ID" "$PHASE_NAME" "$PHASE_STARTED_AT" "$ended" "$forced" "$RELEASE_REFERENCE" "$RELEASE_GIT_COMMIT" "$RELEASE_IMAGE_DIGEST" "$ENTERPRISE_ENVIRONMENT" <<'PY'
import json,pathlib,sys
out,checks,phase,name,started,ended,forced,ref,commit,digest,env=sys.argv[1:]
rows=[json.loads(x) for x in pathlib.Path(checks).read_text().splitlines() if x.strip()]
status='PASS' if rows and all(x['status']=='PASS' for x in rows) else 'FAIL'
if forced=='FAIL': status='FAIL'
d={'schemaVersion':1,'phase':phase,'name':name,'status':status,'startedAt':started,'endedAt':ended,'release':{'reference':ref,'gitCommit':commit,'imageDigest':digest,'environment':env},'checks':rows}
pathlib.Path(out).write_text(json.dumps(d,indent=2,sort_keys=True)+'\n')
raise SystemExit(0 if status=='PASS' else 1)
PY
}
enterprise_sha256(){ if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1"|awk '{print $1}'; else shasum -a 256 "$1"|awk '{print $1}'; fi; }
enterprise_write_checksum(){ printf '%s  %s\n' "$(enterprise_sha256 "$1")" "$(basename "$1")" > "$2"; }
enterprise_copy_input(){ local src="$1" dst="$2"; enterprise_require_file "$src"; cp -- "$src" "$dst"; }
