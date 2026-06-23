#!/usr/bin/env python3
from __future__ import annotations
import argparse, hashlib, json
from datetime import datetime, timezone
from pathlib import Path


def digest(path: Path) -> str:
    h=hashlib.sha256()
    with path.open('rb') as f:
        for block in iter(lambda:f.read(1024*1024),b''): h.update(block)
    return h.hexdigest()


def main()->int:
 p=argparse.ArgumentParser(); p.add_argument('--run-dir',type=Path,required=True); p.add_argument('--mode',choices=('preflight','repo','full'),required=True); p.add_argument('--attestation',type=Path); p.add_argument('--output',type=Path,required=True); p.add_argument('--checksums',type=Path,required=True); a=p.parse_args()
 results={}; errors=[]
 for phase in [f'63{letter}' for letter in 'ABCDEFGHI']:
  path=a.run_dir/phase/'result.json'
  if not path.is_file(): errors.append(f'missing result: {phase}'); continue
  results[phase]=json.loads(path.read_text(encoding='utf-8'))
 expected='PASS' if a.mode=='full' else None
 if expected:
  for phase,data in results.items():
   if data.get('status')!='PASS': errors.append(f"{phase} must be PASS, got {data.get('status')}")
 else:
  for phase,data in results.items():
   if data.get('status') in {'FAIL','BLOCKED'}: errors.append(f"{phase} is {data.get('status')} during {a.mode}")
 commits={d.get('gitCommit') for d in results.values() if d.get('gitCommit') and d.get('gitCommit')!='unavailable'}
 if len(commits)>1: errors.append('phase results reference different Git commits')
 attestation=None
 if a.mode=='full':
  if not a.attestation or not a.attestation.is_file(): errors.append('signed UAT entry attestation is missing')
  else:
   attestation=json.loads(a.attestation.read_text(encoding='utf-8'))
   if attestation.get('schemaVersion')!=1: errors.append('UAT entry attestation schemaVersion must equal 1')
   for role in ('engineeringLead','qaLead','sreLead','securityLead','changeManager'):
    if not isinstance(attestation.get(role),str) or not attestation[role].strip(): errors.append(f'{role} is missing')
   for key in ('allCriticalFindingsClosed','runtimeEvidenceReviewed','goNoGoApproved'):
    if attestation.get(key) is not True: errors.append(f'{key} must be true')
 files=[]
 excluded={a.output.resolve(),a.checksums.resolve()}
 for path in sorted(a.run_dir.rglob('*')):
  relative = path.relative_to(a.run_dir).as_posix()
  if path.is_file() and path.resolve() not in excluded and not relative.startswith('63J/'):
   files.append({'path':relative,'bytes':path.stat().st_size,'sha256':digest(path)})
 a.checksums.write_text(''.join(f"{item['sha256']}  {item['path']}\n" for item in files),encoding='utf-8')
 files.append({'path':a.checksums.relative_to(a.run_dir).as_posix(),'bytes':a.checksums.stat().st_size,'sha256':digest(a.checksums)})
 gate='PASS' if a.mode=='full' and not errors else ('PREPARED' if a.mode!='full' and not errors else 'BLOCKED')
 doc={'schemaVersion':1,'phase':'63A-63J','mode':a.mode,'runId':a.run_dir.name,'generatedAt':datetime.now(timezone.utc).isoformat().replace('+00:00','Z'),'gate':gate,'errors':errors,'gitCommits':sorted(commits),'phaseStatuses':{k:v.get('status') for k,v in sorted(results.items())},'attestation':attestation,'files':files}
 a.output.write_text(json.dumps(doc,indent=2,sort_keys=True)+'\n',encoding='utf-8')
 print(f'Phase 63 evidence manifest: {gate} ({len(files)} artifacts)')
 for e in errors: print('  ERROR:',e)
 if a.mode=='full': return 0 if gate=='PASS' else 1
 return 0 if gate=='PREPARED' else 1
if __name__=='__main__': raise SystemExit(main())
