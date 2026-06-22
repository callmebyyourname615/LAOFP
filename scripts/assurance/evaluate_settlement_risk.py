#!/usr/bin/env python3
import argparse,yaml
from lib import *
p=argparse.ArgumentParser(); [p.add_argument(x,required=True) for x in ['--snapshot','--liquidity-policy','--collateral-policy','--exposure-policy','--thresholds','--now','--report-output','--decision-output']]; a=p.parse_args(); s=load(a.snapshot); lp=yaml.safe_load(open(a.liquidity_policy)); cp=yaml.safe_load(open(a.collateral_policy)); checks=[]
def ck(i,o,d=''): checks.append({'id':i,'status':'PASS' if o else 'FAIL','detail':d})
ck('snapshot.fresh',age_minutes(s['capturedAt'],a.now)<=yaml.safe_load(open(a.thresholds))['snapshotMaxAgeMinutes']); ck('settlement.mismatch',s.get('settlementMismatchMinorUnits')==0); ck('gridlock.monitoring',s.get('gridlockDetectionStatus')=='PASS')
for x in s.get('participants',[]):
 i=x.get('participantId','unknown'); head=x.get('availableMinorUnits',0)-x.get('reservedMinorUnits',0)+x.get('eligibleCollateralAfterHaircutMinorUnits',0); ck(f'{i}.headroom',head>=x.get('minimumOperatingMinorUnits',0)); ck(f'{i}.queueAge',x.get('oldestQueueAgeSeconds',999)<=lp['maximumQueueAgeSeconds']); ck(f'{i}.exposure',x.get('currentExposureMinorUnits',0)<=x.get('exposureLimitMinorUnits',0));
 if x.get('riskTier') in ('CRITICAL','HIGH'): ck(f'{i}.prefunded',x.get('prefundingStatus')=='PASS')
 for c in x.get('collateral',[]): ck(f"{i}.collateral.{c.get('id')}",c.get('type') in cp['eligibleTypes'] and c.get('valuationAgeHours',999)<=cp['staleValuationHours'])
status=pass_status(checks); dump(a.report_output,result(status,checks,participantCount=len(s.get('participants',[])))); dump(a.decision_output,{'schemaVersion':1,'decision':'SETTLEMENT_RISK_WITHIN_LIMITS' if status=='PASS' else 'HOLD_NEW_OUTBOUND','failedControls':[x['id'] for x in checks if x['status']=='FAIL']}); raise SystemExit(0 if status=='PASS' else 1)
