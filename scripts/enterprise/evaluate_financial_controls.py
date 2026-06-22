#!/usr/bin/env python3
import argparse
from lib import load,write,ensure_fresh,now_utc,nested,compare
p=argparse.ArgumentParser(); p.add_argument('--snapshot',required=True); p.add_argument('--catalog',required=True); p.add_argument('--thresholds',required=True); p.add_argument('--now'); p.add_argument('--report-output',required=True); p.add_argument('--decision-output',required=True); a=p.parse_args()
s=load(a.snapshot); c=load(a.catalog); t=load(a.thresholds); now=now_utc(a.now); errors=[]
try: ensure_fresh(s,t['freshness']['defaultSnapshotMaxAgeSeconds'],now)
except Exception as e: errors.append(str(e))
rows=[]
for ctrl in c.get('controls',[]):
    metric=ctrl['metric']; value=s.get(metric)
    threshold=ctrl.get('threshold')
    if threshold is None: threshold=nested(t,ctrl['thresholdFrom'])
    ok=isinstance(value,(int,float)) and compare(value,ctrl['operator'],threshold)
    rows.append({'id':ctrl['id'],'metric':metric,'value':value,'operator':ctrl['operator'],'threshold':threshold,'severity':ctrl['severity'],'status':'PASS' if ok else 'FAIL'})
    if not ok: errors.append(f"{ctrl['id']} failed")
if s.get('snapshotIsolation') not in ('REPEATABLE_READ','SERIALIZABLE'): errors.append('financial snapshot must use REPEATABLE_READ or SERIALIZABLE')
if s.get('currencyPrecisionViolations',1)!=0: errors.append('currency precision violation detected')
if s.get('settlementMismatchMinorUnits',1)!=0: errors.append('settlement mismatch detected')
if s.get('outboxMissingForCommittedTransactions',1)!=0: errors.append('missing outbox event for committed transaction')
if not s.get('financialControllerOwner'): errors.append('financial controller owner missing')
status='PASS' if not errors else 'FAIL'; decision='CONTINUE' if status=='PASS' else 'FREEZE_AFFECTED_SETTLEMENT'
write(a.report_output,{'schemaVersion':1,'status':status,'controls':rows,'errors':errors,'capturedAt':s.get('capturedAt'),'evaluatedAt':now.isoformat().replace('+00:00','Z')})
write(a.decision_output,{'schemaVersion':1,'decision':decision,'reasonCodes':[x['id'] for x in rows if x['status']=='FAIL']+(['ADDITIONAL_INTEGRITY_FAILURE'] if errors and all(x['status']=='PASS' for x in rows) else []),'incidentRequired':status!='PASS','evidencePreservationRequired':status!='PASS'})
if errors: raise SystemExit('\n'.join(errors))
