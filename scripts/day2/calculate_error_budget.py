#!/usr/bin/env python3
import argparse,json,pathlib,sys,yaml

def main():
 p=argparse.ArgumentParser();p.add_argument('--catalog',required=True);p.add_argument('--metrics',required=True);p.add_argument('--thresholds',required=True);p.add_argument('--output',required=True);p.add_argument('--decision-output',required=True);p.add_argument('--now');a=p.parse_args()
 catalog=yaml.safe_load(pathlib.Path(a.catalog).read_text()); metrics=json.loads(pathlib.Path(a.metrics).read_text()); thresholds=yaml.safe_load(pathlib.Path(a.thresholds).read_text())
 import datetime
 rows=[]; block=False
 captured=datetime.datetime.fromisoformat(metrics['capturedAt'].replace('Z','+00:00')); now=datetime.datetime.fromisoformat(a.now.replace('Z','+00:00')) if a.now else datetime.datetime.now(datetime.timezone.utc); age=(now-captured).total_seconds();
 if age<0 or age>float(thresholds['slo']['maximumDataAgeSeconds']): block=True
 for service in catalog['services']:
  for objective in service['objectives']:
   key=f"{service['id']}.{objective['id']}"; m=metrics.get('objectives',{}).get(key)
   if not m: rows.append({'id':key,'status':'MISSING'}); block=True; continue
   target=float(objective['targetPercent']); achieved=float(m['achievedPercent']); allowed=max(100-target,0.000001); consumed=max(0.0,100-achieved); remaining=max(0.0,100-(consumed/allowed*100))
   status='PASS' if achieved>=target else 'BREACH'; block|=status!='PASS'; rows.append({'id':key,'targetPercent':target,'achievedPercent':achieved,'remainingErrorBudgetPercent':round(remaining,6),'status':status})
 min_remaining=min((r.get('remainingErrorBudgetPercent',0) for r in rows),default=0); minimum=float(thresholds['slo']['minimumRemainingErrorBudgetPercent'])
 decision='BLOCK' if block or min_remaining<minimum or metrics.get('criticalIncidentOpen',False) else 'ALLOW'
 report={'schemaVersion':1,'capturedAt':metrics.get('capturedAt'),'objectives':rows,'minimumRemainingErrorBudgetPercent':min_remaining,'decision':decision,'dataAgeSeconds':age}
 pathlib.Path(a.output).write_text(json.dumps(report,indent=2,sort_keys=True)+'\n'); pathlib.Path(a.decision_output).write_text(json.dumps({'schemaVersion':1,'decision':decision,'reason':'SLO breach or insufficient budget' if decision=='BLOCK' else 'SLO and budget gates passed','reportSha256':__import__('hashlib').sha256(pathlib.Path(a.output).read_bytes()).hexdigest()},indent=2,sort_keys=True)+'\n')
 return 0 if decision=='ALLOW' else 1
if __name__=='__main__':sys.exit(main())
