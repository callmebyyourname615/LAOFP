#!/usr/bin/env python3
import argparse, hashlib, json, re, sys
from pathlib import Path
p=argparse.ArgumentParser(); p.add_argument('--root',type=Path,default=Path('.')); p.add_argument('--output',type=Path,required=True); p.add_argument('--allow-missing',action='store_true'); a=p.parse_args()
root=a.root.resolve(); d=root/'src/main/resources/db/migration'; found={}; duplicates=[]
for f in sorted(d.glob('V*.sql')):
 m=re.match(r'V(\d+)__',f.name)
 if not m: continue
 v=int(m.group(1));
 if v in found: duplicates.append(v)
 found[v]=f
reserved={88,89,90,98,99}; expected=set(range(1,107))-reserved; missing=sorted(expected-set(found)); unexpected=sorted(set(found)-expected-reserved)
files=[{'version':v,'path':found[v].relative_to(root).as_posix(),'sha256':hashlib.sha256(found[v].read_bytes()).hexdigest()} for v in sorted(found)]
certified=not missing and not duplicates and not unexpected and len(found)==99 and max(found or [0])==106
result={'schemaVersion':1,'latest':max(found or [0]),'count':len(found),'expectedCount':99,'reservedGaps':sorted(reserved),'missingVersions':missing,'duplicateVersions':duplicates,'unexpectedVersions':unexpected,'certified':certified,'files':files}
a.output.parent.mkdir(parents=True,exist_ok=True); a.output.write_text(json.dumps(result,indent=2,sort_keys=True)+'\n')
print(json.dumps({k:v for k,v in result.items() if k!='files'},indent=2,sort_keys=True))
sys.exit(0 if certified or a.allow_missing else 1)
