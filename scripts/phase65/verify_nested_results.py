#!/usr/bin/env python3
import argparse,json,sys
from pathlib import Path
p=argparse.ArgumentParser(); p.add_argument('--root',type=Path,required=True); p.add_argument('--prefix',required=True); p.add_argument('--expected',type=int,required=True); p.add_argument('--output',type=Path,required=True); a=p.parse_args(); files=sorted(a.root.rglob('result.json')); results=[]; errors=[]
for f in files:
 try:
  d=json.loads(f.read_text())
  if str(d.get('phase','')).startswith(a.prefix): results.append({'path':f.as_posix(),**d})
 except Exception as e: errors.append(f'{f}: {e}')
if len(results)!=a.expected: errors.append(f'expected {a.expected} {a.prefix} results, found {len(results)}')
for r in results:
 if r.get('status')!='PASS': errors.append(f"{r.get('phase')} status is {r.get('status')}")
out={'schemaVersion':1,'passed':not errors,'results':results,'errors':errors}; a.output.parent.mkdir(parents=True,exist_ok=True); a.output.write_text(json.dumps(out,indent=2)+'\n'); print('PASS' if not errors else '\n'.join('FAIL: '+x for x in errors)); sys.exit(0 if not errors else 1)
