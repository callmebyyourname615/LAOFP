#!/usr/bin/env python3
from __future__ import annotations
import argparse, datetime as dt, json, pathlib
try: import yaml
except ImportError as exc: raise SystemExit('PyYAML is required') from exc

def main()->int:
    ap=argparse.ArgumentParser(); ap.add_argument('--policy',default='config/phase55-hypercare-policy.yaml')
    ap.add_argument('--observations',required=True); ap.add_argument('--incidents',required=True)
    ap.add_argument('--reconciliation',required=True); ap.add_argument('--alerts',required=True); ap.add_argument('--output',required=True)
    a=ap.parse_args(); policy=yaml.safe_load(pathlib.Path(a.policy).read_text())
    obs=json.loads(pathlib.Path(a.observations).read_text()); inc=json.loads(pathlib.Path(a.incidents).read_text())
    rec=json.loads(pathlib.Path(a.reconciliation).read_text()); alerts=json.loads(pathlib.Path(a.alerts).read_text())
    errors=[]; start=dt.datetime.fromisoformat(obs['startedAt'].replace('Z','+00:00')); end=dt.datetime.fromisoformat(obs['endedAt'].replace('Z','+00:00'))
    duration=(end-start).total_seconds()/3600
    if duration<policy['exitCriteria']['minimumDurationHours']: errors.append('hypercare duration below minimum')
    required=set(policy['requiredSignals']); present=set(obs.get('signals',{}))
    missing=sorted(required-present)
    if missing: errors.append('missing required signals: '+', '.join(missing))
    failing=sorted(name for name in required & present if obs.get('signals',{}).get(name,{}).get('status')!='PASS')
    if failing: errors.append('signals not PASS: '+', '.join(failing))
    if int(inc.get('criticalOpen',0))!=0: errors.append('open critical incidents exist')
    if int(inc.get('highOpen',0))!=0: errors.append('open high incidents exist')
    if rec.get('status')!='PASS': errors.append('reconciliation is not PASS')
    if int(alerts.get('criticalFiring',0))!=0: errors.append('critical alerts are firing')
    if alerts.get('deliveryVerified') is not True: errors.append('alert delivery is not verified')
    report={'schemaVersion':1,'status':'PASS' if not errors else 'FAIL','durationHours':round(duration,2),'missingSignals':missing,'failingSignals':failing,'criticalOpen':int(inc.get('criticalOpen',0)),'highOpen':int(inc.get('highOpen',0)),'reconciliationStatus':rec.get('status'),'criticalAlertsFiring':int(alerts.get('criticalFiring',0)),'errors':errors}
    pathlib.Path(a.output).write_text(json.dumps(report,indent=2,sort_keys=True)+'\n'); print(json.dumps({'status':report['status'],'durationHours':report['durationHours']})); return 0 if not errors else 2
if __name__=='__main__': raise SystemExit(main())
