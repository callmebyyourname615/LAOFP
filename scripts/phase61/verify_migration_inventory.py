#!/usr/bin/env python3
from __future__ import annotations
import argparse, hashlib, json, re
from datetime import datetime, timezone
from pathlib import Path

def sha(path:Path)->str: return hashlib.sha256(path.read_bytes()).hexdigest()
def main()->int:
 p=argparse.ArgumentParser(); p.add_argument('--migration-dir',type=Path,required=True); p.add_argument('--latest',type=int,required=True); p.add_argument('--count',type=int,required=True); p.add_argument('--reserved',type=int,nargs='*',default=[]); p.add_argument('--output',type=Path,required=True); a=p.parse_args()
 files=[]; errors=[]
 for path in sorted(a.migration_dir.glob('V*__*.sql')):
  m=re.fullmatch(r'V(\d+)__([A-Za-z0-9_\-]+)\.sql',path.name)
  if not m: errors.append(f'invalid migration filename: {path.name}'); continue
  files.append({'version':int(m.group(1)),'name':path.name,'sha256':sha(path),'bytes':path.stat().st_size})
 versions=[f['version'] for f in files]
 duplicates=sorted({v for v in versions if versions.count(v)>1})
 if duplicates: errors.append(f'duplicate versions: {duplicates}')
 if len(files)!=a.count: errors.append(f'expected {a.count} migrations, found {len(files)}')
 if max(versions,default=0)!=a.latest: errors.append(f'expected latest V{a.latest}, found V{max(versions,default=0)}')
 missing=sorted(set(range(1,a.latest+1))-set(versions))
 if missing!=sorted(a.reserved): errors.append(f'missing versions {missing} do not equal reserved {sorted(a.reserved)}')
 doc={'schemaVersion':1,'generatedAt':datetime.now(timezone.utc).isoformat().replace('+00:00','Z'),'passed':not errors,'latest':a.latest,'count':len(files),'reserved':missing,'migrations':files,'errors':errors}
 a.output.parent.mkdir(parents=True,exist_ok=True); a.output.write_text(json.dumps(doc,indent=2,sort_keys=True)+'\n',encoding='utf-8')
 print(f"Phase 61B migration inventory: {'PASS' if not errors else 'FAIL'}")
 for e in errors: print('  ERROR:',e)
 return 0 if not errors else 1
if __name__=='__main__': raise SystemExit(main())
