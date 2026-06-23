#!/usr/bin/env python3
from __future__ import annotations
import argparse, hashlib, json, re
from datetime import datetime, timezone
from pathlib import Path
PHASES=[f'61{x}' for x in 'ABCDEFGHIJ']; DIGEST=re.compile(r'^sha256:[0-9a-f]{64}$'); COMMIT=re.compile(r'^[0-9a-f]{40}$')
def sha(path:Path)->str:
 h=hashlib.sha256()
 with path.open('rb') as f:
  for chunk in iter(lambda:f.read(1024*1024),b''): h.update(chunk)
 return h.hexdigest()
def main()->int:
 p=argparse.ArgumentParser(); p.add_argument('--run-dir',type=Path,required=True); p.add_argument('--commit',required=True); p.add_argument('--application-image-digest',required=True); p.add_argument('--migration-image-digest',required=True); p.add_argument('--approval',type=Path,required=True); p.add_argument('--output',type=Path,required=True); a=p.parse_args(); errors=[]
 if not COMMIT.fullmatch(a.commit): errors.append('commit must be 40 lowercase hex characters')
 if not DIGEST.fullmatch(a.application_image_digest): errors.append('application image digest is invalid')
 if not DIGEST.fullmatch(a.migration_image_digest): errors.append('migration image digest is invalid')
 approval=json.loads(a.approval.read_text());
 if approval.get('schemaVersion')!=1 or approval.get('decision')!='APPROVE_PHASE54_ENTRY': errors.append('approval decision must be APPROVE_PHASE54_ENTRY')
 for key in ('engineeringLead','qaLead','securityLead','sreLead','productOwner','changeManager','signedAt','changeReference'):
  if not isinstance(approval.get(key),str) or not approval[key].strip(): errors.append(f'approval.{key} is required')
 phase_entries={}; commits=set()
 for phase in PHASES:
  result=a.run_dir/phase/'result.json'
  if not result.is_file(): errors.append(f'missing {phase}/result.json'); continue
  d=json.loads(result.read_text()); commits.add(d.get('gitCommit'))
  if d.get('status')!='PASS': errors.append(f'{phase} status is {d.get("status")}')
  phase_entries[phase]={'status':d.get('status'),'path':result.relative_to(a.run_dir).as_posix(),'sha256':sha(result)}
 if commits!={a.commit}: errors.append(f'phase result commits do not match requested commit: {sorted(str(x) for x in commits)}')
 artifacts=[]
 for path in sorted(a.run_dir.rglob('*')):
  if not path.is_file() or path.resolve()==a.output.resolve(): continue
  artifacts.append({'path':path.relative_to(a.run_dir).as_posix(),'sizeBytes':path.stat().st_size,'sha256':sha(path)})
 doc={'schemaVersion':1,'generatedAt':datetime.now(timezone.utc).isoformat().replace('+00:00','Z'),'status':'PASS' if not errors else 'FAIL','gitCommit':a.commit,'applicationImageDigest':a.application_image_digest,'migrationImageDigest':a.migration_image_digest,'phases':phase_entries,'approval':approval,'artifacts':artifacts,'errors':errors}
 a.output.parent.mkdir(parents=True,exist_ok=True); a.output.write_text(json.dumps(doc,indent=2,sort_keys=True)+'\n')
 print(f"Phase 61 evidence manifest: {doc['status']} ({len(artifacts)} artifacts)")
 for e in errors: print('  ERROR:',e)
 return 0 if not errors else 1
if __name__=='__main__': raise SystemExit(main())
