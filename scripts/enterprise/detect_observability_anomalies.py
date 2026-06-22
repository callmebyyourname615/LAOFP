#!/usr/bin/env python3
import argparse,datetime as dt
from lib import load,write,ensure_fresh,now_utc,robust_z,parse_time
p=argparse.ArgumentParser(); p.add_argument('--snapshot',required=True); p.add_argument('--policy',required=True); p.add_argument('--seasonal-baselines',required=True); p.add_argument('--correlation-rules',required=True); p.add_argument('--suppression-policy',required=True); p.add_argument('--thresholds',required=True); p.add_argument('--now'); p.add_argument('--anomaly-output',required=True); p.add_argument('--correlation-output',required=True); a=p.parse_args()
s=load(a.snapshot); policy=load(a.policy); seasons=load(a.seasonal_baselines); correlations=load(a.correlation_rules); suppress_policy=load(a.suppression_policy); th=load(a.thresholds); now=now_utc(a.now); errors=[]
try: ensure_fresh(s,th['freshness']['defaultSnapshotMaxAgeSeconds'],now)
except Exception as e: errors.append(str(e))
season=s.get('season')
if season not in seasons.get('seasons',[]): errors.append('unknown or missing seasonal baseline')
suppressions={x.get('signal'):x for x in s.get('suppressions',[])}; anomaly_rows=[]; critical_unack=0
for signal,row in s.get('signals',{}).items():
    current=row.get('current'); baseline=row.get('baseline',[]); signal_errors=[]
    if not isinstance(current,(int,float)) or not isinstance(baseline,list) or len(baseline)<policy.get('minimumBaselineSamples',30) or any(not isinstance(x,(int,float)) for x in baseline):
        signal_errors.append('invalid or insufficient baseline'); z=float('inf')
    else: z=abs(robust_z(float(current),[float(x) for x in baseline]))
    severity='CRITICAL' if z>=policy['criticalThreshold'] else 'WARNING' if z>=policy['warningThreshold'] else 'NORMAL'
    suppressed=False; sup=suppressions.get(signal)
    if sup:
        try:
            expires=parse_time(sup['expiresAt']); duration=(expires-parse_time(sup['startsAt'])).total_seconds()/3600
            valid=expires>now and duration<=suppress_policy['maximumSuppressionHours'] and bool(sup.get('owner')) and bool(sup.get('reason'))
            if severity=='CRITICAL': valid=valid and len(set(sup.get('approvers',[])))>=2
            suppressed=valid
            if not valid: signal_errors.append('invalid or expired suppression')
        except Exception: signal_errors.append('invalid suppression timestamps')
    acknowledged=bool(row.get('acknowledged'))
    if severity=='CRITICAL' and not acknowledged and not suppressed: critical_unack+=1
    anomaly_rows.append({'signal':signal,'current':current,'robustZScore':None if z==float('inf') else round(z,4),'severity':severity,'suppressed':suppressed,'acknowledged':acknowledged,'errors':signal_errors})
    errors.extend(f'{signal}: {e}' for e in signal_errors)
if critical_unack>th['observability']['maximumUnacknowledgedCriticalAnomalies']: errors.append('unacknowledged critical anomalies exceed threshold')
active={x['signal'] for x in anomaly_rows if x['severity'] in ('WARNING','CRITICAL') and not x['suppressed']}; correlated=[]
for rule in correlations.get('rules',[]):
    matches=sorted(active.intersection(rule.get('signals',[])))
    if len(matches)>=rule.get('minimumMatches',2): correlated.append({'id':rule['id'],'severity':rule['severity'],'signals':matches})
status='PASS' if not errors else 'FAIL'
write(a.anomaly_output,{'schemaVersion':1,'status':status,'season':season,'anomalies':anomaly_rows,'criticalUnacknowledged':critical_unack,'errors':errors,'evaluatedAt':now.isoformat().replace('+00:00','Z')})
write(a.correlation_output,{'schemaVersion':1,'status':status,'correlations':correlated,'activeSignalCount':len(active)})
if errors: raise SystemExit('\n'.join(errors))
