#!/usr/bin/env python3
import argparse,hashlib,json
from pathlib import Path
p=argparse.ArgumentParser(); p.add_argument('--root',required=True); p.add_argument('--output',required=True); p.add_argument('--release-reference',required=True); p.add_argument('--git-commit',required=True); p.add_argument('--image-digest',required=True); a=p.parse_args(); root=Path(a.root).resolve(); out=Path(a.output).resolve(); items=[]
for f in sorted(x for x in root.rglob('*') if x.is_file() and x.resolve()!=out):
 if f.is_symlink(): raise SystemExit('symlink evidence prohibited')
 rel=f.resolve().relative_to(root)
 if 'logs' in rel.parts or rel.name == 'checks.jsonl' or rel.name in {'evidence-manifest.sig','evidence-manifest-verification.txt'}: continue
 items.append({'path':str(rel),'size':f.stat().st_size,'sha256':hashlib.sha256(f.read_bytes()).hexdigest()})
obj={'schemaVersion':1,'excludedVolatilePaths':['**/logs/**','**/checks.jsonl','**/evidence-manifest.sig','**/evidence-manifest-verification.txt'],'release':{'reference':a.release_reference,'gitCommit':a.git_commit,'imageDigest':a.image_digest},'artifacts':items}; out.parent.mkdir(parents=True,exist_ok=True); out.write_text(json.dumps(obj,indent=2,sort_keys=True)+'\n'); print(hashlib.sha256(out.read_bytes()).hexdigest())
