#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
p67_require_identity
PHASE_ID=67E
p67_require_environment production
p67_require_production_confirmation
p67_begin 67E "Canary Five Percent Health Gate"
failed=0
if [[ "$PHASE67_MODE" == preflight ]]; then
  p67_run_check tooling-ready bash -c 'test -f scripts/golive/55g-production-canary.sh && test -f scripts/golive/verify_stage_metrics.py; exit 3' || failed=1
else
  p67_require_phase67_pass 67A 67B 67C 67D
  p67_run_check phase55g-pass p67_require_phase55_pass 55G || failed=1
  p67_run_check build-canary-signals python3 - "$PHASE55_ROOT/phases/55G" "$PHASE_DIR/canary-signals.json" <<'PY' || failed=1
import json,pathlib,sys
root=pathlib.Path(sys.argv[1]); out=pathlib.Path(sys.argv[2]); errors=[]
def read(name):
 p=root/name
 if not p.is_file() or p.is_symlink(): errors.append(f'missing or unsafe {name}'); return {}
 try: return json.loads(p.read_text())
 except Exception: errors.append(f'invalid JSON {name}'); return {}
metrics=read('stage-metrics.json'); recon=read('reconciliation.json'); canary=read('canary-5-percent.json')
by_id={str(x.get('id')):x for x in metrics.get('checks',[]) if isinstance(x,dict)}
def failed(mid): return by_id.get(mid,{}).get('status')!='PASS'
signals={
 'errorRateExceeded':failed('http-error-ratio'),
 'p95LatencyExceeded':failed('http-p95-seconds'),
 'p99LatencyExceeded':failed('http-p99-seconds'),
 'kafkaLagExceeded':failed('kafka-consumer-lag'),
 'outboxBacklogExceeded':failed('outbox-backlog'),
 'databasePoolExceeded':failed('database-pool-utilization'),
 'criticalAlertFiring':failed('critical-alerts'),
 'reconciliationMismatch':recon.get('status')!='PASS' or failed('reconciliation-unmatched'),
 'financialMismatch':recon.get('status')!='PASS' or failed('settlement-failures'),
 'criticalSecurityIncident':False,
 'dataLossSuspected':False,
 'duplicateBusinessReference':False,
}
if metrics.get('status')!='PASS': errors.append('stage metrics are not PASS')
if recon.get('status')!='PASS': errors.append('reconciliation is not PASS')
if canary.get('status')!='PASS' or int(canary.get('weightPercent',0))!=5: errors.append('canary evidence is not PASS at 5 percent')
out.write_text(json.dumps({'schemaVersion':1,'sourcePhase':'55G','signals':signals,'sourceErrors':errors},indent=2,sort_keys=True)+'\n')
raise SystemExit(0 if not errors else 2)
PY
  p67_run_check canary-decision python3 scripts/phase67/phase67_control.py decision \
    --policy "$PHASE67_POLICY" --input "$PHASE_DIR/canary-signals.json" --stage 5 \
    --output "$PHASE_DIR/canary-decision.json" --allowed-decision CONTINUE \
    --reference "$RELEASE_REFERENCE" --rc-id "$RELEASE_RC_ID" --git-commit "$RELEASE_GIT_COMMIT" \
    --application-digest "$RELEASE_APP_IMAGE_DIGEST" --migration-digest "$RELEASE_MIGRATION_IMAGE_DIGEST" \
    --environment "$PHASE67_ENVIRONMENT" --mode "$PHASE67_MODE" || failed=1
fi
if (( failed )); then p67_write_result FAIL; exit 1; fi
p67_write_result
