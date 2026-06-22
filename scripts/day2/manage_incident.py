#!/usr/bin/env python3
import argparse,datetime,json,pathlib,sys,yaml
ALLOWED={'DETECTED':['ACKNOWLEDGED'],'ACKNOWLEDGED':['ASSIGNED'],'ASSIGNED':['MITIGATING'],'MITIGATING':['RECOVERED'],'RECOVERED':['RECONCILED'],'RECONCILED':['MONITORING'],'MONITORING':['CLOSED'],'CLOSED':['POSTMORTEM_COMPLETE']}
def parse(ts):return datetime.datetime.fromisoformat(ts.replace('Z','+00:00'))
def main():
 p=argparse.ArgumentParser();p.add_argument('--incident',required=True);p.add_argument('--severity-policy',default='incident/severity-policy.yaml');p.add_argument('--transition');p.add_argument('--actor');p.add_argument('--audit-only',action='store_true');p.add_argument('--output',required=True);a=p.parse_args();path=pathlib.Path(a.incident);d=json.loads(path.read_text());policy=yaml.safe_load(pathlib.Path(a.severity_policy).read_text());allowed_sev={x['id']:x for x in policy['severities']};errors=[]
 for key in ['incidentId','severity','status','startedAt','owner']:
  if not d.get(key):errors.append(f'missing {key}')
 if d.get('severity') not in allowed_sev:errors.append('invalid severity')
 if d.get('status') not in set(ALLOWED)|{'POSTMORTEM_COMPLETE'}:errors.append('invalid status')
 if d.get('status') not in ('DETECTED','POSTMORTEM_COMPLETE') and not d.get('acknowledgedAt'):errors.append('acknowledgedAt required')
 if d.get('acknowledgedAt') and d.get('severity') in allowed_sev:
  elapsed=(parse(d['acknowledgedAt'])-parse(d['startedAt'])).total_seconds();limit=allowed_sev[d['severity']]['acknowledgeSeconds']
  if elapsed>limit:errors.append('acknowledgement SLA exceeded')
 overdue=[x for x in d.get('correctiveActions',[]) if x.get('status')!='DONE' and x.get('dueAt') and parse(x['dueAt'])<datetime.datetime.now(datetime.timezone.utc)]
 if overdue:errors.append('overdue corrective actions')
 if a.transition:
  if not a.actor:errors.append('actor required')
  if a.transition not in ALLOWED.get(d.get('status'),[]):errors.append('invalid transition')
  if not errors:d.setdefault('timeline',[]).append({'at':datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat().replace('+00:00','Z'),'actor':a.actor,'from':d['status'],'to':a.transition});d['status']=a.transition;path.write_text(json.dumps(d,indent=2,sort_keys=True)+'\n')
 status='PASS' if not errors else 'FAIL';pathlib.Path(a.output).write_text(json.dumps({'schemaVersion':1,'status':status,'incidentId':d.get('incidentId'),'errors':errors,'currentStatus':d.get('status')},indent=2,sort_keys=True)+'\n');return 0 if status=='PASS' else 1
if __name__=='__main__':sys.exit(main())
