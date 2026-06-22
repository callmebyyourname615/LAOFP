#!/usr/bin/env bash
set -Eeuo pipefail
DAY2_PLAN="${DAY2_PLAN:-config/phase56-day2-plan.yaml}"
DAY2_THRESHOLDS="${DAY2_THRESHOLDS:-config/phase56-thresholds.yaml}"
DAY2_ROOT="${DAY2_ROOT:-build/phase56-day2}"
day2_die(){ printf 'ERROR: %s\n' "$*" >&2; exit 1; }
day2_now(){ date -u +%Y-%m-%dT%H:%M:%SZ; }
day2_require_command(){ command -v "$1" >/dev/null 2>&1 || day2_die "required command not found: $1"; }
day2_require_identity(){
 : "${DAY2_ENVIRONMENT:?DAY2_ENVIRONMENT is required}"; : "${RELEASE_REFERENCE:?RELEASE_REFERENCE is required}";
 : "${RELEASE_GIT_COMMIT:?RELEASE_GIT_COMMIT is required}"; : "${RELEASE_IMAGE_DIGEST:?RELEASE_IMAGE_DIGEST is required}";
 case "$DAY2_ENVIRONMENT" in production|operations|security|compliance|dr) ;; *) day2_die "unsupported DAY2_ENVIRONMENT: $DAY2_ENVIRONMENT";; esac
 [[ "$RELEASE_REFERENCE" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{2,127}$ ]] || day2_die "invalid RELEASE_REFERENCE"
 [[ "$RELEASE_GIT_COMMIT" =~ ^[a-f0-9]{40}$ ]] || day2_die "RELEASE_GIT_COMMIT must be a full lowercase SHA"
 [[ "$RELEASE_IMAGE_DIGEST" =~ ^sha256:[a-f0-9]{64}$ ]] || day2_die "invalid RELEASE_IMAGE_DIGEST"
 [[ -f "$DAY2_PLAN" && -f "$DAY2_THRESHOLDS" ]] || day2_die "Phase 56 plan or thresholds missing"
}
day2_require_environment(){ local ok=false; for e in "$@"; do [[ "$DAY2_ENVIRONMENT" == "$e" ]] && ok=true; done; $ok || day2_die "$PHASE_ID cannot run in $DAY2_ENVIRONMENT"; }
day2_require_production_confirmation(){ [[ "${PRODUCTION_EXECUTION_CONFIRMATION:-}" == I_UNDERSTAND_THIS_OPERATES_ON_PRODUCTION ]] || day2_die "production confirmation is required"; }
day2_require_phase_pass(){
 local phase result
 for phase in "$@"; do result="$DAY2_ROOT/phases/$phase/result.json"; [[ -f "$result" ]] || day2_die "missing prerequisite $phase";
 python3 - "$result" "$phase" "$RELEASE_REFERENCE" "$RELEASE_GIT_COMMIT" "$RELEASE_IMAGE_DIGEST" <<'PY'
import json,sys
p,phase,ref,commit,digest=sys.argv[1:]
d=json.load(open(p,encoding='utf-8')); r=d.get('release',{})
if d.get('phase')!=phase or d.get('status')!='PASS': raise SystemExit(f'{phase} is not PASS')
if (r.get('reference'),r.get('gitCommit'),r.get('imageDigest'))!=(ref,commit,digest): raise SystemExit(f'{phase} release identity mismatch')
PY
 done
}
day2_phase_begin(){
 PHASE_ID="$1"; PHASE_NAME="$2"; [[ "$PHASE_ID" =~ ^56[A-J]$ ]] || day2_die "invalid phase id"
 PHASE_DIR="$DAY2_ROOT/phases/$PHASE_ID"
 if [[ -d "$PHASE_DIR" ]] && find "$PHASE_DIR" -mindepth 1 -print -quit | grep -q .; then
  [[ "${DAY2_RERUN_CONFIRMATION:-}" == I_UNDERSTAND_THIS_ARCHIVES_THE_PREVIOUS_ATTEMPT ]] || day2_die "$PHASE_ID evidence exists; rerun confirmation required"
  archive="$DAY2_ROOT/attempts/${PHASE_ID}-$(date -u +%Y%m%dT%H%M%SZ)-$$"; mkdir -p "$(dirname "$archive")"; mv "$PHASE_DIR" "$archive"
 fi
 PHASE_LOG_DIR="$PHASE_DIR/logs"; PHASE_CHECKS_FILE="$PHASE_DIR/checks.jsonl"; PHASE_STARTED_AT="$(day2_now)"; mkdir -p "$PHASE_LOG_DIR"; : > "$PHASE_CHECKS_FILE"
 export PHASE_ID PHASE_NAME PHASE_DIR PHASE_LOG_DIR PHASE_CHECKS_FILE PHASE_STARTED_AT
}
day2_record_check(){ python3 - "$PHASE_CHECKS_FILE" "$1" "$2" "$3" "$4" <<'PY'
import json,pathlib,sys
p,i,s,c,l=sys.argv[1:]
with pathlib.Path(p).open('a',encoding='utf-8') as f:f.write(json.dumps({'id':i,'status':s,'exitCode':int(c),'log':l},sort_keys=True)+'\n')
PY
}
day2_run_check(){ local id="$1"; shift; [[ "$id" =~ ^[a-z0-9][a-z0-9._-]*$ ]] || day2_die "invalid check id"; local log="$PHASE_LOG_DIR/$id.log" rc status; printf '[%s] START %s\n' "$(day2_now)" "$id" > "$log"; set +e; "$@" >> "$log" 2>&1; rc=$?; set -e; [[ $rc -eq 0 ]] && status=PASS || status=FAIL; printf '[%s] %s exit=%s\n' "$(day2_now)" "$status" "$rc" >> "$log"; day2_record_check "$id" "$status" "$rc" "phases/$PHASE_ID/logs/$id.log"; return "$rc"; }
day2_write_result(){
 local forced="${1:-}" ended; ended="$(day2_now)"
 python3 - "$PHASE_DIR/result.json" "$PHASE_CHECKS_FILE" "$PHASE_ID" "$PHASE_NAME" "$PHASE_STARTED_AT" "$ended" "$forced" "$RELEASE_REFERENCE" "$RELEASE_GIT_COMMIT" "$RELEASE_IMAGE_DIGEST" "$DAY2_ENVIRONMENT" <<'PY'
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
day2_sha256(){ if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1"|awk '{print $1}'; else shasum -a 256 "$1"|awk '{print $1}'; fi; }
day2_write_checksum(){ printf '%s  %s\n' "$(day2_sha256 "$1")" "$(basename "$1")" > "$2"; }
