#!/usr/bin/env python3
import argparse, datetime, hashlib, json, os
from pathlib import Path
ap=argparse.ArgumentParser(); ap.add_argument('--phase71-root',required=True); ap.add_argument('--attestation',required=True); ap.add_argument('--output',required=True); args=ap.parse_args()
root=Path(args.phase71_root).resolve(); results=[]; errors=[]
for letter in 'ABCDEFGHI':
 found=list(root.glob(f'71{letter}/result.json'))
 if not found: errors.append(f'missing 71{letter}/result.json'); continue
 data=json.loads(found[0].read_text()); results.append(data)
 if data.get('status')!='PASS': errors.append(f'71{letter} status is {data.get("status")}')
commits={x.get('commit') for x in results}; app={x.get('applicationImageDigest') for x in results}; migration={x.get('migrationImageDigest') for x in results}
if len(commits)!=1 or 'unknown' in commits: errors.append('results must share one known commit')
if len(app)!=1 or not next(iter(app), ''): errors.append('results must share application image digest')
if len(migration)!=1 or not next(iter(migration), ''): errors.append('results must share migration image digest')
att=json.loads(Path(args.attestation).read_text());
if att.get('status')!='PASS' or att.get('kind')!='phase54-entry': errors.append('phase54-entry attestation must be PASS')
artifacts=[]
for p in sorted(root.rglob('*')):
 if p.is_file() and p.name not in {'phase54-entry-bundle.json','phase54-entry-bundle.json.sha256'}:
  artifacts.append({'path':str(p.relative_to(root)),'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'size':p.stat().st_size})
out={'schemaVersion':1,'decision':'GO' if not errors else 'NO-GO','generatedAt':datetime.datetime.now(datetime.timezone.utc).isoformat().replace('+00:00','Z'),'commit':next(iter(commits),'unknown'),'applicationImageDigest':next(iter(app),''),'migrationImageDigest':next(iter(migration),''),'results':results,'attestation':att,'artifacts':artifacts,'errors':errors}
p=Path(args.output); p.write_text(json.dumps(out,indent=2,sort_keys=True)+'\n'); (p.parent/(p.name+'.sha256')).write_text(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n')
print(out['decision']); raise SystemExit(0 if not errors else 1)
