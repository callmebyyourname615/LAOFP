#!/usr/bin/env python3
import argparse,pathlib
from lib import load,sha256,safe_relpath
p=argparse.ArgumentParser(); p.add_argument('--manifest',required=True); p.add_argument('--root',required=True); p.add_argument('--release-reference'); p.add_argument('--git-commit'); p.add_argument('--image-digest'); a=p.parse_args()
m=load(a.manifest); root=pathlib.Path(a.root).resolve(); errors=[]
expected=(a.release_reference,a.git_commit,a.image_digest); actual=tuple(m.get('release',{}).get(k) for k in ('reference','gitCommit','imageDigest'))
if any(expected) and actual!=expected: errors.append('release identity mismatch')
seen=set()
for item in m.get('artifacts',[]):
    rel=item.get('path','')
    if not safe_relpath(rel) or rel in seen: errors.append(f'unsafe or duplicate path: {rel}'); continue
    seen.add(rel); f=(root/rel).resolve()
    if root not in f.parents: errors.append(f'path escapes root: {rel}'); continue
    if not f.is_file(): errors.append(f'missing artifact: {rel}'); continue
    if sha256(f)!=item.get('sha256'): errors.append(f'hash mismatch: {rel}')
if not seen: errors.append('manifest contains no artifacts')
if errors: raise SystemExit('\n'.join(errors))
print(f'OK: verified {len(seen)} evidence artifacts')
