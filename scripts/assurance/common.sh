#!/usr/bin/env bash
set -Eeuo pipefail
ASSURANCE_PLAN="${ASSURANCE_PLAN:-config/phase58-assurance-plan.yaml}"
ASSURANCE_THRESHOLDS="${ASSURANCE_THRESHOLDS:-config/phase58-thresholds.yaml}"
ASSURANCE_ROOT="${ASSURANCE_ROOT:-build/phase58-assurance}"
assurance_die(){ printf 'ERROR: %s\n' "$*" >&2; exit 1; }
assurance_now(){ date -u +%Y-%m-%dT%H:%M:%SZ; }
assurance_require_command(){ command -v "$1" >/dev/null 2>&1 || assurance_die "required command not found: $1"; }
assurance_require_identity(){
 : "${ASSURANCE_ENVIRONMENT:?ASSURANCE_ENVIRONMENT is required}"; : "${RELEASE_REFERENCE:?RELEASE_REFERENCE is required}"; : "${RELEASE_GIT_COMMIT:?RELEASE_GIT_COMMIT is required}"; : "${RELEASE_IMAGE_DIGEST:?RELEASE_IMAGE_DIGEST is required}"
 case "$ASSURANCE_ENVIRONMENT" in production|regulatory|operations|security|compliance|financial-control|simulation) ;; *) assurance_die "unsupported ASSURANCE_ENVIRONMENT: $ASSURANCE_ENVIRONMENT";; esac
 [[ "$RELEASE_REFERENCE" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{2,127}$ ]] || assurance_die "invalid RELEASE_REFERENCE"; [[ "$RELEASE_GIT_COMMIT" =~ ^[a-f0-9]{40}$ ]] || assurance_die "RELEASE_GIT_COMMIT must be full lowercase SHA"; [[ "$RELEASE_IMAGE_DIGEST" =~ ^sha256:[a-f0-9]{64}$ ]] || assurance_die "invalid RELEASE_IMAGE_DIGEST"; [[ -f "$ASSURANCE_PLAN" && -f "$ASSURANCE_THRESHOLDS" ]] || assurance_die "Phase 58 configuration missing"
}
assurance_require_environment(){ local ok=false; for e in "$@"; do [[ "$ASSURANCE_ENVIRONMENT" == "$e" ]] && ok=true; done; $ok || assurance_die "$PHASE_ID cannot run in $ASSURANCE_ENVIRONMENT"; }
assurance_require_file(){ [[ -f "$1" ]] || assurance_die "required file not found: $1"; }
assurance_require_phase_pass(){ local p f; for p in "$@"; do f="$ASSURANCE_ROOT/phases/$p/result.json"; assurance_require_file "$f"; python3 - "$f" "$p" "$RELEASE_REFERENCE" "$RELEASE_GIT_COMMIT" "$RELEASE_IMAGE_DIGEST" <<'PY2'
import json,sys
f,p,ref,commit,digest=sys.argv[1:]; d=json.load(open(f)); r=d.get('release',{})
if d.get('phase')!=p or d.get('status')!='PASS': raise SystemExit(f'{p} is not PASS')
if (r.get('reference'),r.get('gitCommit'),r.get('imageDigest'))!=(ref,commit,digest): raise SystemExit(f'{p} release identity mismatch')
PY2
done; }
assurance_phase_begin(){ PHASE_ID="$1"; PHASE_NAME="$2"; [[ "$PHASE_ID" =~ ^58[A-J]$ ]] || assurance_die "invalid phase"; PHASE_DIR="$ASSURANCE_ROOT/phases/$PHASE_ID"; if [[ -d "$PHASE_DIR" ]] && find "$PHASE_DIR" -mindepth 1 -print -quit | grep -q .; then [[ "${ASSURANCE_RERUN_CONFIRMATION:-}" == I_UNDERSTAND_THIS_ARCHIVES_THE_PREVIOUS_ATTEMPT ]] || assurance_die "$PHASE_ID evidence exists; rerun confirmation required"; archive="$ASSURANCE_ROOT/attempts/${PHASE_ID}-$(date -u +%Y%m%dT%H%M%SZ)-$$"; mkdir -p "$(dirname "$archive")"; mv "$PHASE_DIR" "$archive"; fi; PHASE_LOG_DIR="$PHASE_DIR/logs"; PHASE_CHECKS_FILE="$PHASE_DIR/checks.jsonl"; PHASE_STARTED_AT="$(assurance_now)"; mkdir -p "$PHASE_LOG_DIR"; : > "$PHASE_CHECKS_FILE"; export PHASE_ID PHASE_NAME PHASE_DIR PHASE_LOG_DIR PHASE_CHECKS_FILE PHASE_STARTED_AT; }
assurance_record_check(){ python3 - "$PHASE_CHECKS_FILE" "$1" "$2" "$3" "$4" <<'PY2'
import json,pathlib,sys
p,i,s,c,l=sys.argv[1:]; f=pathlib.Path(p).open('a'); f.write(json.dumps({'id':i,'status':s,'exitCode':int(c),'log':l},sort_keys=True)+'\n'); f.close()
PY2
}
assurance_run_check(){ local id="$1"; shift; local log="$PHASE_LOG_DIR/$id.log" rc status; set +e; "$@" >"$log" 2>&1; rc=$?; set -e; [[ $rc -eq 0 ]] && status=PASS || status=FAIL; assurance_record_check "$id" "$status" "$rc" "phases/$PHASE_ID/logs/$id.log"; return "$rc"; }
assurance_write_result(){ local forced="${1:-}" ended; ended="$(assurance_now)"; python3 - "$PHASE_DIR/result.json" "$PHASE_CHECKS_FILE" "$PHASE_ID" "$PHASE_NAME" "$PHASE_STARTED_AT" "$ended" "$forced" "$RELEASE_REFERENCE" "$RELEASE_GIT_COMMIT" "$RELEASE_IMAGE_DIGEST" "$ASSURANCE_ENVIRONMENT" <<'PY2'
import json,pathlib,sys
out,checks,phase,name,started,ended,forced,ref,commit,digest,env=sys.argv[1:]; rows=[json.loads(x) for x in pathlib.Path(checks).read_text().splitlines() if x.strip()]; status='PASS' if rows and all(x['status']=='PASS' for x in rows) else 'FAIL'; status='FAIL' if forced=='FAIL' else status; d={'schemaVersion':1,'phase':phase,'name':name,'status':status,'startedAt':started,'endedAt':ended,'release':{'reference':ref,'gitCommit':commit,'imageDigest':digest,'environment':env},'checks':rows}; pathlib.Path(out).write_text(json.dumps(d,indent=2,sort_keys=True)+'\n'); raise SystemExit(0 if status=='PASS' else 1)
PY2
}
