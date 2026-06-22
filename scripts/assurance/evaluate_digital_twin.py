#!/usr/bin/env python3
import argparse,yaml
from lib import *
p=argparse.ArgumentParser(); [p.add_argument(x,required=True) for x in ['--snapshot','--scenario-policy','--data-policy','--acceptance-policy','--thresholds','--now','--report-output','--certificate-output']]; a=p.parse_args(); s=load(a.snapshot); sp=yaml.safe_load(open(a.scenario_policy)); checks=[]
def ck(i,o,d=''): checks.append({'id':i,'status':'PASS' if o else 'FAIL','detail':d})
ck('snapshot.fresh',age_minutes(s['capturedAt'],a.now)<=yaml.safe_load(open(a.thresholds))['snapshotMaxAgeMinutes']); ds=s.get('dataset',{}); ck('dataset.sanitized',ds.get('sanitized') is True and ds.get('containsProductionIdentifiers') is False); ck('dataset.digest',sha_ok(ds.get('sha256'))); ck('network.isolated',s.get('networkIsolation') is True); res={x['scenarioId']:x for x in s.get('scenarioResults',[])}
for q in sp['requiredScenarios']:
 x=res.get(q); ck(f'{q}.present',bool(x));
 if x: ck(f'{q}.pass',x.get('status')=='PASS'); ck(f'{q}.deterministic',x.get('deterministicReplay') is True); ck(f'{q}.sideEffects',x.get('productionSideEffects') is False); ck(f'{q}.forecast',float(x.get('forecastErrorPercent',999))<=sp['maximumForecastErrorPercent']); ck(f'{q}.financialInvariant',x.get('financialMismatchMinorUnits')==0)
status=pass_status(checks); dump(a.report_output,result(status,checks,scenarioCount=len(res))); dump(a.certificate_output,{'schemaVersion':1,'decision':'DIGITAL_TWIN_CERTIFIED' if status=='PASS' else 'DIGITAL_TWIN_NOT_CERTIFIED','failedControls':[x['id'] for x in checks if x['status']=='FAIL']}); raise SystemExit(0 if status=='PASS' else 1)
