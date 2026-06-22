#!/usr/bin/env python3
import argparse,yaml
from lib import *
p=argparse.ArgumentParser(); [p.add_argument(x,required=True) for x in ['--snapshot','--model-policy','--change-policy','--monitoring-policy','--thresholds','--now','--report-output','--decision-output']]; a=p.parse_args(); s=load(a.snapshot); pol=yaml.safe_load(open(a.model_policy)); checks=[]
def ck(i,o,d=''): checks.append({'id':i,'status':'PASS' if o else 'FAIL','detail':d})
ck('snapshot.fresh',age_minutes(s['capturedAt'],a.now)<=yaml.safe_load(open(a.thresholds))['snapshotMaxAgeMinutes'])
for m in s.get('decisionAssets',[]):
 i=m.get('id','unknown'); ck(f'{i}.owner',bool(m.get('owner'))); ck(f'{i}.approved',m.get('status')=='APPROVED'); ck(f'{i}.immutableVersion',bool(m.get('version')) and m.get('versionImmutable') is True); ck(f'{i}.fourEyes',len(set(m.get('approvedBy',[])))>=2); ck(f'{i}.explainable',m.get('reasonCodeCoveragePercent')==100); ck(f'{i}.rollback',m.get('rollbackTestStatus')=='PASS'); ck(f'{i}.drift',float(m.get('driftScore',999))<=pol['maximumDriftScore']); ck(f'{i}.digest',isinstance(m.get('productionDigest'),str) and m['productionDigest'].startswith('sha256:')); ck(f'{i}.shadow',m.get('unapprovedShadowDecisions',0)==0)
status=pass_status(checks); dump(a.report_output,result(status,checks,assetCount=len(s.get('decisionAssets',[])))); dump(a.decision_output,{'schemaVersion':1,'decision':'PROMOTE_DECISION_ASSETS' if status=='PASS' else 'ROLLBACK_OR_HOLD_DECISION_ASSETS','failedControls':[x['id'] for x in checks if x['status']=='FAIL']}); raise SystemExit(0 if status=='PASS' else 1)
