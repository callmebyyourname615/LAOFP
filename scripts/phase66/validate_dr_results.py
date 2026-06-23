#!/usr/bin/env python3
from __future__ import annotations
import argparse,json
from pathlib import Path
import yaml

def main():
 p=argparse.ArgumentParser(); p.add_argument('--config',required=True); p.add_argument('--evidence-dir'); p.add_argument('--output',required=True); p.add_argument('--contract-only',action='store_true'); a=p.parse_args()
 cfg=yaml.safe_load(Path(a.config).read_text()); rows=[]
 for s in cfg['scenarios']:
  row={'id':s['id'],'required':s.get('required',True),'status':'NOT_RUN' if a.contract_only else 'FAIL'}
  if not a.contract_only:
   candidates=list(Path(a.evidence_dir).rglob(f"*{s['id']}*.json")) if a.evidence_dir else []
   if candidates:
    data=json.loads(candidates[-1].read_text()); row['status']='PASS' if data.get('status')=='PASS' else 'FAIL'; row['evidence']=str(candidates[-1])
  rows.append(row)
 passed=all((not r['required']) or r['status'] in ({'NOT_RUN'} if a.contract_only else {'PASS'}) for r in rows)
 Path(a.output).write_text(json.dumps({'schemaVersion':1,'contractOnly':a.contract_only,'results':rows,'passed':passed},indent=2,sort_keys=True)+'\n')
 return 0 if passed else 2
if __name__=='__main__': raise SystemExit(main())
