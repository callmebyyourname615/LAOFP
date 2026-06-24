#!/usr/bin/env python3
from __future__ import annotations
import argparse, hashlib, json, pathlib, re
EXPECTED=set(range(1,107))-{88,89,90,98,99,102,103}
# V102/V103 are reserved in the authoritative policy; expected count remains 99.

def main()->int:
 ap=argparse.ArgumentParser(); ap.add_argument('--root',default='src/main/resources/db/migration'); ap.add_argument('--output',required=True); ap.add_argument('--allow-incomplete',action='store_true'); a=ap.parse_args()
 root=pathlib.Path(a.root); rows=[]; versions=[]; duplicates=[]; seen={}
 for p in sorted(root.glob('V*.sql')):
  m=re.match(r'V(\d+)__.+\.sql$',p.name)
  if not m: continue
  v=int(m.group(1)); versions.append(v); seen.setdefault(v,[]).append(p.name); rows.append({'version':v,'path':str(p),'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'size':p.stat().st_size})
 duplicates=[{'version':v,'files':f} for v,f in seen.items() if len(f)>1]
 missing=sorted(EXPECTED-set(versions)); unexpected=sorted(set(versions)-EXPECTED)
 latest=max(versions) if versions else None; passed=not missing and not duplicates and latest==106 and len(rows)==99
 out={'schemaVersion':1,'expectedCount':99,'actualCount':len(rows),'expectedLatest':106,'actualLatest':latest,'missingVersions':missing,'unexpectedVersions':unexpected,'duplicates':duplicates,'migrations':rows,'passed':passed}
 pathlib.Path(a.output).write_text(json.dumps(out,indent=2,sort_keys=True)+'\n')
 print(json.dumps({'passed':passed,'actualCount':len(rows),'latest':latest,'missing':missing,'duplicates':len(duplicates)}))
 return 0 if passed or a.allow_incomplete else 2
if __name__=='__main__': raise SystemExit(main())
