#!/usr/bin/env python3
from __future__ import annotations
import argparse, datetime, hashlib, json
from pathlib import Path
p=argparse.ArgumentParser(); p.add_argument('--evidence-root',required=True); p.add_argument('--attestation',required=True); p.add_argument('--output',required=True); p.add_argument('--commit',required=True); p.add_argument('--application-digest',required=True); p.add_argument('--migration-digest',required=True); a=p.parse_args()
root=Path(a.evidence_root).resolve(); att=json.loads(Path(a.attestation).read_text()); items=[]; phases={}
for f in sorted(root.rglob('*')):
 if f.is_file() and f.resolve()!=Path(a.output).resolve(): items.append({'path':str(f.relative_to(root)),'sha256':hashlib.sha256(f.read_bytes()).hexdigest(),'bytes':f.stat().st_size})
for f in root.glob('78?/result.json'):
 d=json.loads(f.read_text()); phases[d['phase']]=d['status']
errors=[f'{phase} is not PASS' for phase in [f'78{x}' for x in 'ABCDEFGHI'] if phases.get(phase)!='PASS']
if att.get('status')!='PASS': errors.append('Phase 54 GO attestation is not PASS')
for key,val in [('commit',a.commit),('applicationImageDigest',a.application_digest),('migrationImageDigest',a.migration_digest)]:
 if att.get(key)!=val: errors.append(key+' mismatch')
out={'schemaVersion':1,'bundleType':'phase78-uat-closure','status':'PASS' if not errors else 'BLOCKED','generatedAt':datetime.datetime.now(datetime.timezone.utc).isoformat().replace('+00:00','Z'),'commit':a.commit,'applicationImageDigest':a.application_digest,'migrationImageDigest':a.migration_digest,'phaseStatuses':phases,'errors':errors,'evidence':items}
Path(a.output).parent.mkdir(parents=True,exist_ok=True); Path(a.output).write_text(json.dumps(out,indent=2,sort_keys=True)+'\n'); print(out['status']); raise SystemExit(0 if not errors else 2)
