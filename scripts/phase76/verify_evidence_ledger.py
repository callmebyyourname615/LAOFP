#!/usr/bin/env python3
import argparse, hashlib, json
from pathlib import Path
p=argparse.ArgumentParser(); p.add_argument('--ledger',required=True); p.add_argument('--root',required=True); a=p.parse_args()
d=json.loads(Path(a.ledger).read_text()); prev='0'*64; errors=[]
for rec in d['entries']:
    path=Path(a.root)/rec['path']
    if not path.is_file(): errors.append(f"missing:{rec['path']}"); continue
    if hashlib.sha256(path.read_bytes()).hexdigest()!=rec['sha256']: errors.append(f"sha256:{rec['path']}")
    if rec['previous_hash']!=prev: errors.append(f"chain:{rec['path']}")
    base={k:v for k,v in rec.items() if k!='record_hash'}; expected=hashlib.sha256(json.dumps(base,sort_keys=True,separators=(',',':')).encode()).hexdigest()
    if expected!=rec['record_hash']: errors.append(f"record_hash:{rec['path']}")
    prev=rec['record_hash']
if prev!=d['tail_hash']: errors.append('tail_hash')
print(json.dumps({'valid':not errors,'errors':errors,'entry_count':len(d['entries']),'tail_hash':prev},indent=2)); raise SystemExit(1 if errors else 0)
