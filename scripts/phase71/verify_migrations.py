#!/usr/bin/env python3
import argparse, hashlib, json, re
from pathlib import Path
root=Path(__file__).resolve().parents[2]; mig=root/'src/main/resources/db/migration'
ap=argparse.ArgumentParser(); ap.add_argument('--output',required=True); ap.add_argument('--allow-missing',action='store_true'); args=ap.parse_args()
items=[]
for p in mig.glob('V*__*.sql'):
 m=re.match(r'V(\d+)__',p.name)
 if m: items.append((int(m.group(1)),p))
items.sort(); versions=[v for v,_ in items]
required=[v for v in range(1,107) if v not in {88,89,90,98,99,102,103}]
missing=[v for v in required if v not in versions]; duplicates=sorted({v for v in versions if versions.count(v)>1})
certified=(not missing and not duplicates and len(items)==99 and max(versions,default=0)==106)
out={'schemaVersion':1,'certified':certified,'count':len(items),'latest':max(versions,default=0),'missing':missing,'duplicates':duplicates,'reservedGaps':[88,89,90,98,99,102,103],'sha256':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for _,p in items}}
Path(args.output).write_text(json.dumps(out,indent=2,sort_keys=True)+'\n')
print(json.dumps({k:out[k] for k in ('certified','count','latest','missing','duplicates')},sort_keys=True))
raise SystemExit(0 if certified or args.allow_missing else 1)
