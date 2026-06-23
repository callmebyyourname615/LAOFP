#!/usr/bin/env python3
import argparse, hashlib, json, sys
from pathlib import Path
p=argparse.ArgumentParser(); p.add_argument('--phase68-root',type=Path,required=True); p.add_argument('--attestation',type=Path,required=True); p.add_argument('--output',type=Path,required=True); a=p.parse_args(); errors=[]
try: att=json.loads(a.attestation.read_text())
except Exception as e: print(f'FAIL: {e}',file=sys.stderr); sys.exit(1)
results=[]
for phase in [f'68{x}' for x in 'ABCDEFGHI']:
 files=list(a.phase68_root.rglob(f'{phase}/result.json'))
 if not files: errors.append(f'missing {phase} result'); continue
 d=json.loads(files[-1].read_text()); results.append(d)
 if d.get('status')!='PASS': errors.append(f'{phase} status is {d.get("status")}')
commit={r.get('commit') for r in results if r.get('commit')}
app={r.get('applicationImageDigest') for r in results if r.get('applicationImageDigest')}
mig={r.get('migrationImageDigest') for r in results if r.get('migrationImageDigest')}
if len(commit)>1: errors.append('phase results use different commits')
if len(app)>1: errors.append('phase results use different application image digests')
if len(mig)>1: errors.append('phase results use different migration image digests')
if int(att.get('p0Blockers',1))!=0: errors.append('kickoff attestation has P0 blockers')
artifacts=[]
for f in sorted(a.phase68_root.rglob('*')):
 if f.is_file() and f.name not in {'phase54-kickoff-bundle.json'}:
  artifacts.append({'path':f.relative_to(a.phase68_root).as_posix(),'sha256':hashlib.sha256(f.read_bytes()).hexdigest(),'size':f.stat().st_size})
out={'schemaVersion':1,'passed':not errors,'errors':errors,'attestation':att,'results':results,'artifacts':artifacts}
a.output.parent.mkdir(parents=True,exist_ok=True); a.output.write_text(json.dumps(out,indent=2,sort_keys=True)+'\n')
print('PASS' if not errors else '\n'.join('FAIL: '+e for e in errors)); sys.exit(0 if not errors else 1)
