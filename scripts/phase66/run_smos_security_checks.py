#!/usr/bin/env python3
from __future__ import annotations
import argparse,json,os,urllib.request,urllib.error
from pathlib import Path
import yaml

def request(base,token,item):
 url=base.rstrip('/')+item['path']; req=urllib.request.Request(url,method=item.get('method','GET'),headers={'Authorization':f'Bearer {token}','Accept':'application/json'})
 try:
  with urllib.request.urlopen(req,timeout=item.get('timeoutSeconds',10)) as r: return r.status
 except urllib.error.HTTPError as e:return e.code

def main():
 p=argparse.ArgumentParser(); p.add_argument('--config',required=True); p.add_argument('--output',required=True); p.add_argument('--contract-only',action='store_true'); a=p.parse_args()
 cfg=yaml.safe_load(Path(a.config).read_text()); rows=[]; base=os.environ.get('SMOS_BASE_URL',''); token=os.environ.get('SMOS_OPERATOR_TOKEN','')
 for item in cfg['checks']:
  row={'id':item['id'],'status':'NOT_RUN'}
  if not a.contract_only:
   code=request(base,token,item); row.update(httpStatus=code,status='PASS' if code in item['expectedStatus'] else 'FAIL')
  rows.append(row)
 passed=all(r['status'] in ({'NOT_RUN'} if a.contract_only else {'PASS'}) for r in rows)
 Path(a.output).write_text(json.dumps({'schemaVersion':1,'contractOnly':a.contract_only,'results':rows,'passed':passed},indent=2,sort_keys=True)+'\n')
 return 0 if passed else 2
if __name__=='__main__':raise SystemExit(main())
