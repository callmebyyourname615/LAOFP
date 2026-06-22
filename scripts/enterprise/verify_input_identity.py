#!/usr/bin/env python3
import argparse
from lib import load,release_identity
p=argparse.ArgumentParser(); p.add_argument('--release-reference',required=True); p.add_argument('--git-commit',required=True); p.add_argument('--image-digest',required=True); p.add_argument('files',nargs='+'); a=p.parse_args()
expected=(a.release_reference,a.git_commit,a.image_digest); errors=[]
for path in a.files:
    data=load(path); actual=release_identity(data)
    if actual!=expected: errors.append(f'input release identity mismatch: {path}')
if errors: raise SystemExit('\n'.join(errors))
print(f'OK: {len(a.files)} input file(s) bound to immutable release identity')
