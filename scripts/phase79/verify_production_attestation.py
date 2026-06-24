#!/usr/bin/env python3
from __future__ import annotations
import argparse,datetime,hashlib,json,re
from pathlib import Path
p=argparse.ArgumentParser(); p.add_argument('--kind',required=True); p.add_argument('--file',required=True); p.add_argument('--output',required=True); p.add_argument('--commit',required=True); p.add_argument('--application-digest',required=True); p.add_argument('--migration-digest',required=True); a=p.parse_args(); d=json.loads(Path(a.file).read_text()); e=[]
for k in ('schemaVersion','kind','status','environment','executedAt','operator','approvals','evidence','commit','applicationImageDigest','migrationImageDigest'):
 if k not in d:e.append('missing '+k)
if d.get('kind')!=a.kind:e.append('kind mismatch')
if d.get('status')!='PASS':e.append('status must be PASS')
if d.get('environment')!='production':e.append('environment must be production')
if d.get('commit')!=a.commit:e.append('commit mismatch')
if d.get('applicationImageDigest')!=a.application_digest:e.append('application digest mismatch')
if d.get('migrationImageDigest')!=a.migration_digest:e.append('migration digest mismatch')
text=json.dumps(d).lower()
for m in ('replace_me','placeholder','todo','tbd','example.invalid','unknown'):
 if m in text:e.append('placeholder marker: '+m)
if len(d.get('approvals',[]))<6:e.append('six approvals required')
for item in d.get('evidence',[]):
 if not re.fullmatch(r'[a-f0-9]{64}',str(item.get('sha256',''))):e.append('invalid evidence hash')
out={'schemaVersion':1,'kind':a.kind,'valid':not e,'errors':e,'sourceSha256':hashlib.sha256(Path(a.file).read_bytes()).hexdigest(),'verifiedAt':datetime.datetime.now(datetime.timezone.utc).isoformat().replace('+00:00','Z')}; Path(a.output).parent.mkdir(parents=True,exist_ok=True); Path(a.output).write_text(json.dumps(out,indent=2,sort_keys=True)+'\n'); print('attestation PASS' if not e else '\n'.join('FAIL: '+x for x in e)); raise SystemExit(0 if not e else 2)
