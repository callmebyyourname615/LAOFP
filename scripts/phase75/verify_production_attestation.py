#!/usr/bin/env python3
from __future__ import annotations
import argparse,hashlib,json,pathlib,re
PLACEHOLDER=re.compile(r'(TODO|REPLACE|EXAMPLE|UNKNOWN|TBD|CHANGEME)',re.I)
KINDS={'production-infrastructure','command-center','security-supply-chain','observability','release-candidate','production-decision'}
def main()->int:
 ap=argparse.ArgumentParser(); ap.add_argument('--kind',required=True,choices=sorted(KINDS)); ap.add_argument('--file',required=True); ap.add_argument('--output',required=True); ap.add_argument('--commit',required=True); ap.add_argument('--application-digest',required=True); ap.add_argument('--migration-digest',required=True); a=ap.parse_args(); p=pathlib.Path(a.file); d=json.loads(p.read_text()); errors=[]
 for k in ['schemaVersion','kind','status','commit','applicationImageDigest','migrationImageDigest','environment','approvals','evidenceFiles']:
  if k not in d: errors.append('missing '+k)
 if d.get('kind')!=a.kind: errors.append('kind mismatch')
 if d.get('status')!='PASS': errors.append('status must be PASS')
 if d.get('environment')!='production': errors.append('environment must be production')
 if d.get('commit')!=a.commit: errors.append('commit mismatch')
 if d.get('applicationImageDigest')!=a.application_digest: errors.append('application digest mismatch')
 if d.get('migrationImageDigest')!=a.migration_digest: errors.append('migration digest mismatch')
 if not isinstance(d.get('approvals'),list) or not d.get('approvals'): errors.append('approvals empty')
 if not isinstance(d.get('evidenceFiles'),list) or not d.get('evidenceFiles'): errors.append('evidenceFiles empty')
 if PLACEHOLDER.search(json.dumps(d,sort_keys=True)): errors.append('placeholder detected')
 if a.kind=='production-infrastructure':
  for key in ('contractPassed','migrationDryRunPassed','externalConnectivityPassed','financialBaselineApproved'):
   if d.get(key) is not True: errors.append(key+' must be true')
 if a.kind=='command-center':
  for key in ('rollbackRehearsed','canaryGatesApproved','communicationPlanApproved'):
   if d.get(key) is not True: errors.append(key+' must be true')
  required={'Command Leader','SRE Lead','DBA','Security Lead','Business Lead','Evidence Recorder'}
  roles={x.get('role') for x in d.get('approvals',[]) if isinstance(x,dict) and x.get('approved') is True}
  if not required.issubset(roles): errors.append('all command-center roles required')
 if a.kind=='production-decision':
  if d.get('decision')!='GO': errors.append('production decision must be GO')
  required={'Engineering Lead','QA Lead','Security Lead','SRE Lead','Product Business Owner','Change Manager'}
  roles={x.get('role') for x in d.get('approvals',[]) if isinstance(x,dict) and x.get('approved') is True}
  if not required.issubset(roles): errors.append('all six production approvals required')
 evidence=[]
 for rel in d.get('evidenceFiles',[]):
  ep=pathlib.Path(rel); ep=ep if ep.is_absolute() else (p.parent/ep).resolve()
  if not ep.is_file() or ep.is_symlink(): errors.append('missing evidence '+rel); continue
  evidence.append({'path':str(ep),'sha256':hashlib.sha256(ep.read_bytes()).hexdigest(),'size':ep.stat().st_size})
 out={'schemaVersion':1,'kind':a.kind,'passed':not errors,'errors':errors,'evidence':evidence,'attestationSha256':hashlib.sha256(p.read_bytes()).hexdigest()}; pathlib.Path(a.output).write_text(json.dumps(out,indent=2,sort_keys=True)+'\n'); print(json.dumps({'passed':not errors,'errors':errors})); return 0 if not errors else 2
if __name__=='__main__': raise SystemExit(main())
