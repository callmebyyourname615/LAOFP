#!/usr/bin/env python3
import argparse, json, re
from pathlib import Path
ap=argparse.ArgumentParser(); ap.add_argument('--kind',required=True); ap.add_argument('--file',required=True); ap.add_argument('--output',required=True); args=ap.parse_args()
p=Path(args.file); errors=[]
try: data=json.loads(p.read_text())
except Exception as e: data={}; errors.append(f'invalid JSON: {e}')
required=['schemaVersion','kind','status','environment','executedAt','operator','approver','evidence']
for key in required:
 if not data.get(key): errors.append(f'missing {key}')
 elif isinstance(data.get(key),str) and 'REPLACE_' in data.get(key): errors.append(f'placeholder value in {key}')
if data.get('kind') != args.kind: errors.append(f'kind must be {args.kind}')
if data.get('status') != 'PASS': errors.append('status must be PASS')
if data.get('environment') not in ('uat','production'): errors.append('environment must be uat or production')
if not isinstance(data.get('evidence'),list) or not data.get('evidence'): errors.append('evidence must be a non-empty list')
for e in data.get('evidence',[]):
 if not isinstance(e,dict) or not e.get('path') or not re.fullmatch(r'[0-9a-f]{64}',str(e.get('sha256',''))): errors.append('each evidence item requires path and SHA-256')
 elif 'REPLACE_' in str(e.get('path')) or str(e.get('sha256')) == '0'*64: errors.append('placeholder evidence is not allowed')
out={'schemaVersion':1,'kind':args.kind,'certified':not errors,'errors':errors}
Path(args.output).write_text(json.dumps(out,indent=2,sort_keys=True)+'\n')
print('attestation PASS' if not errors else '\n'.join('FAIL: '+x for x in errors)); raise SystemExit(0 if not errors else 1)
