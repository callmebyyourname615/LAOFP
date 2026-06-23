#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
p67_require_identity
PHASE_ID=67F
p67_require_environment production
p67_require_production_confirmation
p67_begin 67F "Progressive Traffic Promotion Gate"
failed=0
if [[ "$PHASE67_MODE" == preflight ]]; then
  p67_run_check tooling-ready bash -c 'test -f scripts/golive/55h-controlled-cutover.sh && test -f scripts/golive/verify_decision.py; exit 3' || failed=1
else
  p67_require_phase67_pass 67A 67B 67C 67D 67E
  p67_run_check phase55h-pass p67_require_phase55_pass 55H || failed=1
  p67_run_check progressive-stage-evidence python3 - "$PHASE55_ROOT/phases/55H" "$PHASE_DIR/promotion-ledger.json" "$PHASE_DIR/latest-cutover-signals.json" <<'PY' || failed=1
import json,pathlib,sys
root=pathlib.Path(sys.argv[1]); ledger_out=pathlib.Path(sys.argv[2]); signals_out=pathlib.Path(sys.argv[3]); errors=[]; stages=[]
for weight in (25,50,100):
 stage=root/'stages'/f'{weight}.json'; metrics=root/'stages'/f'{weight}-metrics.json'
 if not stage.is_file() or stage.is_symlink(): errors.append(f'missing stage {weight} evidence'); continue
 if not metrics.is_file() or metrics.is_symlink(): errors.append(f'missing stage {weight} metrics'); continue
 try: s=json.loads(stage.read_text()); m=json.loads(metrics.read_text())
 except Exception: errors.append(f'invalid stage {weight} JSON'); continue
 stage_ok=s.get('status')=='PASS' and int(s.get('weightPercent',s.get('stage',0)))==weight
 metric_ok=m.get('status')=='PASS'
 if not stage_ok: errors.append(f'stage {weight} is not PASS')
 if not metric_ok: errors.append(f'stage {weight} metrics are not PASS')
 stages.append({'weightPercent':weight,'stageStatus':s.get('status'),'metricsStatus':m.get('status')})
summary=root/'cutover-summary.json'; rollback=root/'rollback-readiness.json'
for p in (summary,rollback):
 if not p.is_file() or p.is_symlink(): errors.append(f'missing or unsafe {p.name}')
latest_metrics={}
last=root/'stages'/'100-metrics.json'
if last.is_file():
 try: latest_metrics=json.loads(last.read_text())
 except Exception: pass
by_id={str(x.get('id')):x for x in latest_metrics.get('checks',[]) if isinstance(x,dict)}
def bad(mid): return by_id.get(mid,{}).get('status')!='PASS'
signals={'errorRateExceeded':bad('http-error-ratio'),'p95LatencyExceeded':bad('http-p95-seconds'),'p99LatencyExceeded':bad('http-p99-seconds'),'kafkaLagExceeded':bad('kafka-consumer-lag'),'outboxBacklogExceeded':bad('outbox-backlog'),'databasePoolExceeded':bad('database-pool-utilization'),'criticalAlertFiring':bad('critical-alerts'),'reconciliationMismatch':bad('reconciliation-unmatched'),'financialMismatch':bad('settlement-failures'),'criticalSecurityIncident':False,'dataLossSuspected':False,'duplicateBusinessReference':False}
ledger_out.write_text(json.dumps({'schemaVersion':1,'status':'PASS' if not errors else 'FAIL','orderedStages':stages,'errors':errors},indent=2,sort_keys=True)+'\n')
signals_out.write_text(json.dumps({'schemaVersion':1,'sourcePhase':'55H','stage':100,'signals':signals},indent=2,sort_keys=True)+'\n')
raise SystemExit(0 if not errors else 2)
PY
  p67_run_check final-stage-decision python3 scripts/phase67/phase67_control.py decision \
    --policy "$PHASE67_POLICY" --input "$PHASE_DIR/latest-cutover-signals.json" --stage 100 \
    --output "$PHASE_DIR/final-stage-decision.json" --allowed-decision CONTINUE \
    --reference "$RELEASE_REFERENCE" --rc-id "$RELEASE_RC_ID" --git-commit "$RELEASE_GIT_COMMIT" \
    --application-digest "$RELEASE_APP_IMAGE_DIGEST" --migration-digest "$RELEASE_MIGRATION_IMAGE_DIGEST" \
    --environment "$PHASE67_ENVIRONMENT" --mode "$PHASE67_MODE" || failed=1
fi
if (( failed )); then p67_write_result FAIL; exit 1; fi
p67_write_result
