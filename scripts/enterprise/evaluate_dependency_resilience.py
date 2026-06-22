#!/usr/bin/env python3
import argparse
from lib import load,write,ensure_fresh,now_utc
p=argparse.ArgumentParser(); p.add_argument('--snapshot',required=True); p.add_argument('--catalog',required=True); p.add_argument('--degraded-policy',required=True); p.add_argument('--scenarios',required=True); p.add_argument('--thresholds',required=True); p.add_argument('--now'); p.add_argument('--report-output',required=True); p.add_argument('--decisions-output',required=True); a=p.parse_args()
s=load(a.snapshot); cat=load(a.catalog)['dependencies']; policy=load(a.degraded_policy); scenarios=load(a.scenarios)['scenarios']; th=load(a.thresholds); now=now_utc(a.now); errors=[]
try: ensure_fresh(s,th['freshness']['defaultSnapshotMaxAgeSeconds'],now)
except Exception as e: errors.append(str(e))
observed={x.get('id'):x for x in s.get('dependencies',[])}; rows=[]; decisions=[]
for dep,cfg in cat.items():
    row=observed.get(dep); dep_errors=[]
    if not row: dep_errors.append('dependency snapshot missing')
    else:
        for field in policy['requiredFields']:
            if field not in row or row.get(field) in (None,'',[]): dep_errors.append(f'missing {field}')
        if row.get('testedMode')!=cfg['transactionBehavior']: dep_errors.append('tested degraded mode differs from catalog')
        if row.get('observedOutageMinutes',10**9)>cfg['maximumOutageMinutes'] and row.get('recoveryStatus')!='PASS': dep_errors.append('outage exceeded policy without successful recovery')
        if row.get('reconciliationRequired') and row.get('reconciliationStatus')!='PASS': dep_errors.append('required reconciliation not PASS')
        if cfg['criticality']=='critical' and not row.get('circuitBreakerTested'): dep_errors.append('critical dependency circuit breaker not tested')
        if row.get('fallbackUsesStaleData') and row.get('fallbackAgeWithinLimit') is not True: dep_errors.append('stale fallback exceeds allowed age')
    rows.append({'dependency':dep,'criticality':cfg['criticality'],'status':'PASS' if not dep_errors else 'FAIL','errors':dep_errors})
    errors.extend(f'{dep}: {e}' for e in dep_errors)
    decisions.append({'dependency':dep,'configuredBehavior':cfg['transactionBehavior'],'decision':'APPROVED' if not dep_errors else 'BLOCKED'})
scenario_results={x.get('id'):x for x in s.get('scenarioResults',[])}
for sc in scenarios:
    r=scenario_results.get(sc['id'])
    if not r or r.get('status')!='PASS' or r.get('observedMode')!=sc['expectedMode']:
        errors.append(f"scenario not certified: {sc['id']}")
status='PASS' if not errors else 'FAIL'
write(a.report_output,{'schemaVersion':1,'status':status,'dependencies':rows,'scenarioCount':len(scenarios),'errors':errors,'evaluatedAt':now.isoformat().replace('+00:00','Z')})
write(a.decisions_output,{'schemaVersion':1,'status':status,'decisions':decisions,'manualOverrideExecuted':False})
if errors: raise SystemExit('\n'.join(errors))
