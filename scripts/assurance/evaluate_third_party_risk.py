#!/usr/bin/env python3
import argparse,yaml
from lib import *
p=argparse.ArgumentParser(); [p.add_argument(x,required=True) for x in ['--snapshot','--vendor-policy','--concentration-policy','--monitoring-policy','--thresholds','--now','--report-output','--decision-output']]; a=p.parse_args(); s=load(a.snapshot); vp=yaml.safe_load(open(a.vendor_policy)); cp=yaml.safe_load(open(a.concentration_policy)); checks=[]
def ck(i,o,d=''): checks.append({'id':i,'status':'PASS' if o else 'FAIL','detail':d})
ck('snapshot.fresh',age_minutes(s['capturedAt'],a.now)<=yaml.safe_load(open(a.thresholds))['snapshotMaxAgeMinutes'])
for v in s.get('vendors',[]):
 i=v.get('vendorId','unknown'); critical=v.get('criticality')=='CRITICAL'; ck(f'{i}.owner',bool(v.get('serviceOwner'))); ck(f'{i}.dataLocation',bool(v.get('dataLocations'))); ck(f'{i}.subprocessors',v.get('subprocessorInventoryStatus')=='PASS'); ck(f'{i}.rightToAudit',v.get('rightToAudit') is True)
 if critical:
  ck(f'{i}.assurance',(dt(a.now)-dt(v['assuranceDate'])).days<=vp['criticalVendorAssuranceMaxAgeDays']); ck(f'{i}.bcpdr',v.get('bcpDrEvidenceStatus')=='PASS'); ck(f'{i}.exitPlan',v.get('exitPlanStatus')=='PASS'); ck(f'{i}.exitTest',v.get('exitTestStatus')=='PASS'); ck(f'{i}.security',v.get('securityAttestationStatus')=='PASS')
for c in s.get('concentrations',[]): ck(f"concentration.{c.get('category')}",float(c.get('percent',999))<=float(c.get('limitPercent',cp['maximumCriticalVendorConcentrationPercent'])))
status=pass_status(checks); dump(a.report_output,result(status,checks,vendorCount=len(s.get('vendors',[])))); dump(a.decision_output,{'schemaVersion':1,'decision':'THIRD_PARTY_RISK_ACCEPTABLE' if status=='PASS' else 'BLOCK_NEW_DEPENDENCY_USAGE','failedControls':[x['id'] for x in checks if x['status']=='FAIL']}); raise SystemExit(0 if status=='PASS' else 1)
