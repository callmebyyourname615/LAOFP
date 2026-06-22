#!/usr/bin/env python3
import argparse,hashlib,json
from pathlib import Path
p=argparse.ArgumentParser(); p.add_argument('--manifest',required=True); p.add_argument('--root',required=True); a=p.parse_args(); root=Path(a.root).resolve(); d=json.load(open(a.manifest))
for x in d['artifacts']:
 f=(root/x['path']).resolve();
 if root not in f.parents or not f.is_file() or f.is_symlink(): raise SystemExit('unsafe or missing evidence path')
 if f.stat().st_size!=x['size'] or hashlib.sha256(f.read_bytes()).hexdigest()!=x['sha256']: raise SystemExit('evidence mismatch')
print('evidence manifest PASS')
