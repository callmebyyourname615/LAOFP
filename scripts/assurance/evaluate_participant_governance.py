#!/usr/bin/env python3
import argparse,yaml
from lib import *
p=argparse.ArgumentParser(); [p.add_argument(x,required=True) for x in ['--snapshot','--policy','--risk-policy','--thresholds','--now','--report-output','--decision-output']]; a=p.parse_args(); s=load(a.snapshot); pol=yaml.safe_load(open(a.policy)); th=yaml.safe_load(open(a.thresholds)); checks=[]
def ck(i,o,d=''): checks.append({'id':i,'status':'PASS' if o else 'FAIL','detail':d})
ck('snapshot.fresh',age_minutes(s['capturedAt'],a.now)<=th['snapshotMaxAgeMinutes'])
for x in s.get('participants',[]):
 i=x.get('participantId','unknown'); active=x.get('status')=='ACTIVE'; ck(f'{i}.identifier',bool(i) and i!='unknown'); ck(f'{i}.owner',bool(x.get('riskOwner'))); ck(f'{i}.tier',x.get('riskTier') in ('CRITICAL','HIGH','STANDARD'))
 if active:
  ck(f'{i}.certificate',dt(x['certificateExpiresAt'])>dt(a.now) and (dt(x['certificateExpiresAt'])-dt(a.now)).days>=pol['minimumRemainingDays'])
  for q in pol['requiredChecks']: ck(f'{i}.{q}',x.get('checks',{}).get(q)=='PASS')
 if x.get('status')=='SUSPENDED': ck(f'{i}.trafficBlocked',x.get('trafficEnabled') is False)
status=pass_status(checks); dump(a.report_output,result(status,checks,participantCount=len(s.get('participants',[])))); dump(a.decision_output,{'schemaVersion':1,'decision':'CERTIFICATION_CURRENT' if status=='PASS' else 'SUSPEND_NONCOMPLIANT_PARTICIPANT','failedControls':[x['id'] for x in checks if x['status']=='FAIL']}); raise SystemExit(0 if status=='PASS' else 1)
