#!/usr/bin/env python3
import hashlib, json, pathlib, sys
manifest=json.load(open(sys.argv[1], encoding='utf-8'))
for item in manifest.get('files', []):
    p=pathlib.Path(item['path'])
    digest=hashlib.sha256(p.read_bytes()).hexdigest()
    if digest != item['sha256']:
        raise SystemExit(f"hash mismatch: {p}")
print('iso validation pack manifest valid')
