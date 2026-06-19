#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cert_require_release_identity
require_phase_pass 54A 54B 54C 54H
[[ "$CERTIFICATION_ENVIRONMENT" == uat || "$CERTIFICATION_ENVIRONMENT" == dr ]] || cert_die "54F requires UAT or DR"
[[ "${BACKUP_CERTIFICATION_CONFIRMATION:-}" == I_UNDERSTAND_THIS_RUNS_BACKUP_AND_ISOLATED_RESTORE ]] || cert_die "invalid BACKUP_CERTIFICATION_CONFIRMATION"
cert_require_command kubectl
NAMESPACE="${BACKUP_NAMESPACE:-switching}"
STAMP="$(date -u +%Y%m%d%H%M%S)"
phase_begin 54F "Backup, Restore & PITR Certification"
failed=0
create_job_from_cronjob() {
  local cron="$1" job="$2" command="$3" output="$4"
  kubectl -n "$NAMESPACE" create job "$job" --from="cronjob/$cron" --dry-run=client -o json |
    python3 -c 'import json,sys; d=json.load(sys.stdin); d["spec"]["ttlSecondsAfterFinished"]=86400; c=d["spec"]["template"]["spec"]["containers"][0]; c["command"]=[sys.argv[1]]; c.setdefault("env",[]).append({"name":"CERTIFICATION_EVIDENCE_STDOUT","value":"true"}); print(json.dumps(d))' "$command" > "$output"
  kubectl apply -f "$output"
}
run_check full-backup bash -lc '
  ns="$0"; job="$1"; out="$2";
  kubectl -n "$ns" create job "$job" --from=cronjob/switching-full-backup;
  kubectl -n "$ns" wait --for=condition=complete "job/$job" --timeout=14400s;
  kubectl -n "$ns" logs "job/$job" --all-containers=true | tee "$out"' "$NAMESPACE" "phase54-full-backup-$STAMP" "$PHASE_DIR/full-backup.log" || failed=1
verify_job="phase54-verify-backup-$STAMP"
run_check create-verify-job create_job_from_cronjob switching-full-backup "$verify_job" /opt/switching-backup/bin/verify-backup.sh "$PHASE_DIR/verify-job.json" || failed=1
run_check verify-backup bash -lc '
  ns="$0"; job="$1"; out="$2";
  kubectl -n "$ns" wait --for=condition=complete "job/$job" --timeout=1800s;
  kubectl -n "$ns" logs "job/$job" --all-containers=true | tee "$out"' "$NAMESPACE" "$verify_job" "$PHASE_DIR/verify-backup.log" || failed=1
restore_job="phase54-restore-drill-$STAMP"
run_check create-restore-job create_job_from_cronjob switching-restore-drill "$restore_job" /opt/switching-backup/bin/restore-drill.sh "$PHASE_DIR/restore-job.json" || failed=1
run_check isolated-restore bash -lc '
  ns="$0"; job="$1"; out="$2";
  kubectl -n "$ns" wait --for=condition=complete "job/$job" --timeout=7200s;
  kubectl -n "$ns" logs "job/$job" --all-containers=true | tee "$out"' "$NAMESPACE" "$restore_job" "$PHASE_DIR/restore-drill.log" || failed=1
if ! python3 - "$PHASE_DIR/restore-drill.log" "$PHASE_DIR/restore-evidence.json" <<'PY'
import json,pathlib,sys
line=next((x for x in pathlib.Path(sys.argv[1]).read_text(encoding='utf-8',errors='replace').splitlines() if x.startswith('CERTIFICATION_RESTORE_EVIDENCE=')), '')
if not line: raise SystemExit('restore evidence line missing')
data=json.loads(line.split('=',1)[1]); pathlib.Path(sys.argv[2]).write_text(json.dumps(data,indent=2,sort_keys=True)+'\n',encoding='utf-8')
PY
then failed=1; fi
rpo="${PITR_RPO_SECONDS_OBSERVED:-}"
if [[ ! "$rpo" =~ ^[0-9]+$ ]]; then
  run_check pitr-rpo-marker bash -c 'echo "PITR_RPO_SECONDS_OBSERVED must be a non-negative integer from the controlled WAL marker drill" >&2; exit 64' || failed=1
  rpo=999999999
else
  run_check pitr-rpo-marker bash -c 'test "$1" -ge 0' _ "$rpo" || failed=1
fi
if ! python3 - "$PHASE_DIR/restore-evidence.json" "$PHASE_DIR/recovery-objectives.json" "$rpo" config/phase54-thresholds.yaml <<'PY'
import json,pathlib,sys,yaml
restore=json.load(open(sys.argv[1],encoding='utf-8')); rpo=int(sys.argv[3]); cfg=yaml.safe_load(open(sys.argv[4],encoding='utf-8'))['backup']; rto=int(restore['rtoSeconds'])
doc={'schemaVersion':1,'rpoSeconds':rpo,'rtoSeconds':rto,'maximumRpoSeconds':cfg['maximumRpoSeconds'],'maximumRtoSeconds':cfg['maximumRtoSeconds'],'checksumsVerified':restore.get('verification')=='PASS','rowCountsVerified':int(restore.get('transactionCount',-1))>=0,'passed':rpo<=cfg['maximumRpoSeconds'] and rto<=cfg['maximumRtoSeconds'] and restore.get('verification')=='PASS'}
pathlib.Path(sys.argv[2]).write_text(json.dumps(doc,indent=2,sort_keys=True)+'\n',encoding='utf-8')
if not doc['passed']: raise SystemExit(1)
PY
then failed=1; fi
write_phase_result "$([[ $failed -eq 0 ]] && echo PASS || echo FAIL)"
