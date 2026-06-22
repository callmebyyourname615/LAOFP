#!/usr/bin/env python3
import argparse,json,pathlib,sys
from lib import sha256,write
p=argparse.ArgumentParser(); p.add_argument('--root',required=True); p.add_argument('--output',required=True); p.add_argument('--include',action='append',default=[]); p.add_argument('--release-reference',required=True); p.add_argument('--git-commit',required=True); p.add_argument('--image-digest',required=True); a=p.parse_args()
root=pathlib.Path(a.root).resolve(); artifacts=[]
patterns=a.include or ['**/*']
seen=set()
for pattern in patterns:
    for f in root.glob(pattern):
        if not f.is_file() or f.resolve()==pathlib.Path(a.output).resolve(): continue
        rel=f.relative_to(root).as_posix()
        if rel in seen or rel.endswith('.sig'): continue
        seen.add(rel); artifacts.append({'path':rel,'sha256':sha256(f),'sizeBytes':f.stat().st_size})
artifacts.sort(key=lambda x:x['path'])
if not artifacts: raise SystemExit('no evidence artifacts found')
write(a.output,{'schemaVersion':1,'release':{'reference':a.release_reference,'gitCommit':a.git_commit,'imageDigest':a.image_digest},'artifacts':artifacts})
