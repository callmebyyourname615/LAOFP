#!/usr/bin/env python3
import hashlib,json,pathlib,sys
root=pathlib.Path(sys.argv[1]).resolve()
out=pathlib.Path(sys.argv[2]).resolve()
if not root.is_dir(): raise SystemExit('artifact directory missing')
items=[]
for p in sorted(x for x in root.rglob('*') if x.is_file() and x.resolve()!=out):
    rel=p.resolve().relative_to(root)
    if '..' in rel.parts or p.is_symlink(): raise SystemExit('unsafe artifact path')
    items.append({'path':str(rel),'size':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()})
if not items: raise SystemExit('no report artifacts found')
payload={'version':1,'artifacts':items}
out.write_text(json.dumps(payload,indent=2,sort_keys=True)+'\n',encoding='utf-8')
print(hashlib.sha256(out.read_bytes()).hexdigest())
