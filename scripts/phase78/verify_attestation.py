#!/usr/bin/env python3
from __future__ import annotations
import argparse, datetime, hashlib, json, re
from pathlib import Path
p=argparse.ArgumentParser(); p.add_argument('--kind',required=True); p.add_argument('--file',required=True); p.add_argument('--output',required=True); p.add_argument('--commit',default=''); p.add_argument('--application-digest',default=''); p.add_argument('--migration-digest',default=''); a=p.parse_args()
data=json.loads(Path(a.file).read_text()); errors=[]
for k in ['schemaVersion','kind','status','environment','executedAt','operator','approvals','evidence']:
 if k not in data: errors.append('missing '+k)
text=json.dumps(data).lower()
for marker in ('replace_me','todo','example.invalid','placeholder','tbd','unknown'):
 if marker in text: errors.append('placeholder marker: '+marker)
if data.get('kind')!=a.kind: errors.append('kind mismatch')
if data.get('status')!='PASS': errors.append('status must be PASS')
if data.get('environment')!='uat': errors.append('environment must be uat')
if a.commit and data.get('commit')!=a.commit: errors.append('commit mismatch')
if a.application_digest and data.get('applicationImageDigest')!=a.application_digest: errors.append('application digest mismatch')
if a.migration_digest and data.get('migrationImageDigest')!=a.migration_digest: errors.append('migration digest mismatch')
if len(data.get('approvals',[]))<2: errors.append('at least two approvals required')
if not data.get('evidence'): errors.append('evidence list required')
for item in data.get('evidence',[]):
 if not isinstance(item,dict) or not re.fullmatch(r'[a-f0-9]{64}',str(item.get('sha256',''))): errors.append('invalid evidence sha256')
out={'schemaVersion':1,'kind':a.kind,'valid':not errors,'errors':errors,'sourceSha256':hashlib.sha256(Path(a.file).read_bytes()).hexdigest(),'verifiedAt':datetime.datetime.now(datetime.timezone.utc).isoformat().replace('+00:00','Z')}
Path(a.output).parent.mkdir(parents=True,exist_ok=True); Path(a.output).write_text(json.dumps(out,indent=2,sort_keys=True)+'\n'); print('attestation PASS' if not errors else '\n'.join('FAIL: '+e for e in errors)); raise SystemExit(0 if not errors else 2)
