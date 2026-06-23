#!/usr/bin/env python3
from __future__ import annotations
import argparse, json, os, re, socket, ssl, subprocess, time
from datetime import datetime, timezone
from pathlib import Path
import yaml
PLACEHOLDER=re.compile(r'(?i)(change_me|replace-me|example\.com|todo|tbd|latest|snapshot)')

def load_env(path:Path|None)->dict[str,str]:
 values={}
 if path:
  for raw in path.read_text(encoding='utf-8').splitlines():
   line=raw.strip()
   if not line or line.startswith('#') or '=' not in line: continue
   k,v=line.split('=',1); values[k.strip()]=v.strip().strip('"').strip("'")
 return values

def tcp(host:str,port:int,timeout:float=3)->tuple[bool,str]:
 try:
  with socket.create_connection((host,port),timeout=timeout): return True,'connected'
 except Exception as e: return False,str(e)

def main()->int:
 p=argparse.ArgumentParser(); p.add_argument('--contract',type=Path,required=True); p.add_argument('--env-file',type=Path); p.add_argument('--base-url'); p.add_argument('--preflight',action='store_true'); p.add_argument('--output',type=Path,required=True); a=p.parse_args()
 contract=yaml.safe_load(a.contract.read_text(encoding='utf-8')) or {}; errors=[]; checks=[]
 if contract.get('schemaVersion')!=1: errors.append('contract schemaVersion must equal 1')
 required=contract.get('requiredVariables') or []
 if len(required)!=len(set(required)): errors.append('requiredVariables contains duplicates')
 text=a.contract.read_text(encoding='utf-8')
 if PLACEHOLDER.search(text): errors.append('contract contains a placeholder or mutable artifact marker')
 env={**os.environ,**load_env(a.env_file)}
 for key in required:
  value=env.get(key,'')
  present=bool(value)
  checks.append({'name':f'env:{key}','passed':present if not a.preflight else True,'detail':'present' if present else 'missing'})
  if not a.preflight and not present: errors.append(f'missing required variable: {key}')
 for endpoint in contract.get('tcpEndpoints') or []:
  host=env.get(endpoint['hostVariable'],''); port_text=env.get(endpoint['portVariable'],'')
  if a.preflight: checks.append({'name':endpoint['name'],'passed':True,'detail':'probe contract present'}); continue
  try: port=int(port_text)
  except Exception: errors.append(f"{endpoint['name']}: invalid port"); continue
  ok,detail=tcp(host,port); checks.append({'name':endpoint['name'],'passed':ok,'detail':detail})
  if not ok: errors.append(f"{endpoint['name']}: {detail}")
 if not a.preflight:
  if not a.base_url or not a.base_url.startswith('https://'): errors.append('UAT base URL must use https://')
  digest_values=[env.get('APPLICATION_IMAGE_DIGEST',''),env.get('MIGRATION_IMAGE_DIGEST','')]
  for label,value in zip(('application','migration'),digest_values):
   if not re.fullmatch(r'sha256:[0-9a-f]{64}',value): errors.append(f'{label} image is not digest-pinned')
  max_skew=int(contract.get('maximumClockSkewSeconds',5)); now=time.time()
  try:
   out=subprocess.check_output(['date','+%s'],text=True).strip(); skew=abs(now-int(out)); checks.append({'name':'time-sync','passed':skew<=max_skew,'detail':f'{skew:.3f}s'})
   if skew>max_skew: errors.append(f'clock skew {skew:.3f}s exceeds {max_skew}s')
  except Exception as e: errors.append(f'time sync probe failed: {e}')
 doc={'schemaVersion':1,'generatedAt':datetime.now(timezone.utc).isoformat().replace('+00:00','Z'),'mode':'preflight' if a.preflight else 'live','passed':not errors,'checks':checks,'errors':errors}
 a.output.parent.mkdir(parents=True,exist_ok=True); a.output.write_text(json.dumps(doc,indent=2,sort_keys=True)+'\n',encoding='utf-8')
 print(f"Phase 61C UAT contract: {'PASS' if not errors else 'FAIL'}")
 for e in errors: print('  ERROR:',e)
 return 0 if not errors else 1
if __name__=='__main__': raise SystemExit(main())
