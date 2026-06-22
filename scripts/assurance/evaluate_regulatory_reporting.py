#!/usr/bin/env python3
import argparse,yaml
from lib import *
p=argparse.ArgumentParser(); [p.add_argument(x,required=True) for x in ['--snapshot','--catalog','--policy','--thresholds','--now','--report-output','--decision-output']]; a=p.parse_args()
s=load(a.snapshot); cat=yaml.safe_load(open(a.catalog)); pol=yaml.safe_load(open(a.policy)); th=yaml.safe_load(open(a.thresholds)); checks=[]; reports={x['reportId']:x for x in s.get('reports',[])}
def ck(i,ok,d=''): checks.append({'id':i,'status':'PASS' if ok else 'FAIL','detail':d})
ck('snapshot.fresh',age_minutes(s['capturedAt'],a.now)<=th['snapshotMaxAgeMinutes'])
for rid in cat['reports']:
 r=reports.get(rid); ck(f'{rid}.present',bool(r));
 if not r: continue
 ck(f'{rid}.schema',r.get('schemaValid') is True); ck(f'{rid}.reconciled',r.get('reconciled') is True)
 ck(f'{rid}.dualControl',len(set(r.get('approvedBy',[])))>=pol['minimumApprovers'])
 ck(f'{rid}.deadline',dt(r['submittedAt'])<=dt(r['deadlineAt']))
 ck(f'{rid}.ack',r.get('transportAck')=='ACCEPTED')
 ck(f'{rid}.evidence',sha_ok(r.get('evidenceSha256')))
 if r.get('transportAck')=='REJECTED': ck(f'{rid}.resubmission',bool(r.get('resubmissionPlan')))
status=pass_status(checks); report=result(status,checks,reportCount=len(reports)); decision={'schemaVersion':1,'decision':'SUBMISSIONS_ASSURED' if status=='PASS' else 'BLOCK_SUPERVISORY_SIGNOFF','failedControls':[x['id'] for x in checks if x['status']=='FAIL']}; dump(a.report_output,report); dump(a.decision_output,decision); raise SystemExit(0 if status=='PASS' else 1)
