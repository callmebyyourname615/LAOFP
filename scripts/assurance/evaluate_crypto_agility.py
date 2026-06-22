#!/usr/bin/env python3
import argparse,yaml
from lib import *
p=argparse.ArgumentParser(); [p.add_argument(x,required=True) for x in ['--snapshot','--algorithm-policy','--pqc-policy','--key-policy','--thresholds','--now','--report-output','--plan-output']]; a=p.parse_args(); s=load(a.snapshot); alg=yaml.safe_load(open(a.algorithm_policy)); pq=yaml.safe_load(open(a.pqc_policy)); checks=[]
def ck(i,o,d=''): checks.append({'id':i,'status':'PASS' if o else 'FAIL','detail':d})
ck('snapshot.fresh',age_minutes(s['capturedAt'],a.now)<=yaml.safe_load(open(a.thresholds))['snapshotMaxAgeMinutes']); assets=s.get('assets',[]); ck('inventory.nonempty',bool(assets)); ck('cryptoBom.complete',s.get('cryptoBomCoveragePercent')==100)
for x in assets:
 i=x.get('id','unknown'); ck(f'{i}.algorithm',x.get('algorithm') not in alg['prohibited']); ck(f'{i}.backing',x.get('backing') in ('HSM','VAULT')); ck(f'{i}.rotation',dt(x['nextRotationAt'])>=dt(a.now));
 if x.get('type')=='CERTIFICATE': ck(f'{i}.expiry',(dt(x['expiresAt'])-dt(a.now)).days>=alg['certificateMinimumRemainingDays'])
plan=s.get('pqcPlan',{}); ck('pqc.owner',bool(plan.get('owner'))); ck('pqc.targetDate',bool(plan.get('targetDate'))); ck('pqc.hybridPilot',plan.get('hybridPilotStatus') in ('PASS','PLANNED')); ck('pqc.harvestAssessment',plan.get('harvestNowDecryptLaterAssessment')=='COMPLETE')
status=pass_status(checks); dump(a.report_output,result(status,checks,assetCount=len(assets))); dump(a.plan_output,{'schemaVersion':1,'decision':'CRYPTO_AGILITY_READY' if status=='PASS' else 'BLOCK_CRYPTO_CHANGE','pqcPlan':plan,'failedControls':[x['id'] for x in checks if x['status']=='FAIL']}); raise SystemExit(0 if status=='PASS' else 1)
