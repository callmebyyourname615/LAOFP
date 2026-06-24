#!/usr/bin/env python3
from __future__ import annotations
import argparse, hashlib, json, re, subprocess
from pathlib import Path
p=argparse.ArgumentParser(); p.add_argument('--root',default='.'); p.add_argument('--output',required=True); p.add_argument('--expected-commit',default=''); p.add_argument('--delivery',action='store_true'); a=p.parse_args()
root=Path(a.root).resolve(); errors=[]
required={61:'scripts/phase61/run_phase61.sh',64:'scripts/phase64/run_phase64.sh',65:'scripts/phase65/run_phase65.sh',66:'scripts/phase66/run_phase66.sh',67:'scripts/phase67/run_phase67.sh',68:'scripts/phase68/run_phase68.sh',69:'scripts/phase69/run_phase69.sh',70:'scripts/phase70/run_phase70.sh',71:'scripts/phase71/run_phase71.sh',72:'scripts/phase72/run_phase72.sh',73:'scripts/phase73/run_phase73.sh',74:'scripts/phase74/run_phase74.sh',75:'scripts/phase75/run_phase75.sh',76:'scripts/phase76/run_phase76.sh',77:'scripts/phase77/run_phase77.sh'}
missing=[path for path in required.values() if not (root/path).is_file()]
if missing: errors.append('missing authoritative phase source: '+', '.join(missing))
rows=[]; mig=root/'src/main/resources/db/migration'
for f in sorted(mig.glob('V*.sql')):
 m=re.match(r'V(\d+)__',f.name)
 if m: rows.append({'version':int(m.group(1)),'file':f.name,'sha256':hashlib.sha256(f.read_bytes()).hexdigest()})
versions={x['version'] for x in rows}; expected=set(range(1,107))-{88,89,90,98,99}
missing_versions=sorted(expected-versions)
if len(rows)!=101 or missing_versions: errors.append(f'migration contract mismatch: count={len(rows)} expected=101 missing={missing_versions}')
try: commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True,timeout=3).strip()
except Exception: commit='unknown'; errors.append('git commit unavailable')
if a.expected_commit and commit!=a.expected_commit: errors.append(f'commit mismatch: actual={commit} expected={a.expected_commit}')
try: status=subprocess.check_output(['git','status','--porcelain','--untracked-files=no'],cwd=root,text=True,timeout=5).splitlines()
except Exception: status=[]; errors.append('git tracked-status unavailable')
if status: errors.append(f'tracked working tree is not clean: {len(status)} path(s)')
result={'schemaVersion':1,'commit':commit,'expectedCommit':a.expected_commit or None,'missingPhaseSources':missing,'migrationCount':len(rows),'latestMigration':max(versions) if versions else None,'missingMigrationVersions':missing_versions,'trackedChanges':status[:200],'certified':not errors,'deliveryMode':a.delivery,'errors':errors,'migrations':rows}
Path(a.output).parent.mkdir(parents=True,exist_ok=True); Path(a.output).write_text(json.dumps(result,indent=2,sort_keys=True)+'\n')
print('source convergence PASS' if not errors else '\n'.join('BLOCKED: '+e for e in errors)); raise SystemExit(0 if not errors or a.delivery else 2)
