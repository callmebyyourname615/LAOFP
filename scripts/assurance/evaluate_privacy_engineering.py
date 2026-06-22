#!/usr/bin/env python3
import argparse,yaml
from lib import *
p=argparse.ArgumentParser(); [p.add_argument(x,required=True) for x in ['--snapshot','--control-policy','--rights-policy','--breach-policy','--thresholds','--now','--report-output','--decision-output']]; a=p.parse_args(); s=load(a.snapshot); cp=yaml.safe_load(open(a.control_policy)); rp=yaml.safe_load(open(a.rights_policy)); bp=yaml.safe_load(open(a.breach_policy)); th=yaml.safe_load(open(a.thresholds)); checks=[]
def ck(i,o,d=''): checks.append({'id':i,'status':'PASS' if o else 'FAIL','detail':d})
ck('snapshot.fresh',age_minutes(s['capturedAt'],a.now)<=th['snapshotMaxAgeMinutes'])
for d in s.get('datasets',[]):
 i=d.get('dataset','unknown'); ck(f'{i}.purpose',bool(d.get('purpose'))); ck(f'{i}.legalBasis',bool(d.get('legalBasis'))); ck(f'{i}.encrypted',d.get('encrypted') is True); ck(f'{i}.minimized',d.get('minimizationStatus')=='PASS'); ck(f'{i}.nonProdMasked',d.get('nonProductionMasking')=='PASS');
 if d.get('crossBorder'): ck(f'{i}.transferSafeguard',d.get('transferSafeguard')=='VALID')
for c in s.get('rightsCases',[]): ck(f"case.{c.get('caseId')}.sla",c.get('status')=='CLOSED' or dt(c['dueAt'])>=dt(a.now))
for b in s.get('breaches',[]):
 ck(f"breach.{b.get('id')}.assessment",b.get('assessmentStatus')=='COMPLETE');
 if b.get('notificationRequired'): ck(f"breach.{b.get('id')}.notification",b.get('notificationStatus')=='SENT' and dt(b['notifiedAt'])<=dt(b['notificationDeadlineAt']))
status=pass_status(checks); dump(a.report_output,result(status,checks,datasetCount=len(s.get('datasets',[])))); dump(a.decision_output,{'schemaVersion':1,'decision':'PRIVACY_CONTROLS_ASSURED' if status=='PASS' else 'BLOCK_DATA_PROCESSING_CHANGE','failedControls':[x['id'] for x in checks if x['status']=='FAIL']}); raise SystemExit(0 if status=='PASS' else 1)
