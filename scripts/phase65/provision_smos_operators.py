#!/usr/bin/env python3
from __future__ import annotations
import argparse, json, os, stat, sys, urllib.request, urllib.error
from pathlib import Path
REQUIRED={'SYSTEM_ADMIN','OPS_ADMIN','SETTLEMENT_OFFICER','RISK_OFFICER','AUDITOR'}

def main():
 p=argparse.ArgumentParser(); p.add_argument('--plan',type=Path,required=True); p.add_argument('--dry-run',action='store_true'); p.add_argument('--output',type=Path,required=True); a=p.parse_args()
 mode=stat.S_IMODE(a.plan.stat().st_mode)
 if mode & 0o077: raise SystemExit('Provisioning plan must not be group/world readable; chmod 600')
 d=json.loads(a.plan.read_text()); base=d.get('apiBaseUrl','').rstrip('/')
 if not (base.startswith('https://') or base.startswith('http://localhost')): raise SystemExit('apiBaseUrl must use HTTPS (localhost exception only)')
 users=d.get('users') or []; roles={u.get('role') for u in users}
 if not REQUIRED.issubset(roles): raise SystemExit(f'Provisioning plan missing roles: {sorted(REQUIRED-roles)}')
 token=os.getenv('SMOS_BOOTSTRAP_BEARER_TOKEN','')
 if not a.dry_run and not token: raise SystemExit('SMOS_BOOTSTRAP_BEARER_TOKEN is required')
 results=[]
 for u in users:
  env=u.get('passwordEnv',''); password=os.getenv(env,'')
  if not a.dry_run and len(password)<16: raise SystemExit(f'{env} must contain a 16+ character password')
  body={"username":u['username'],"email":u['email'],"fullName":u['fullName'],"initialPassword":password if not a.dry_run else 'DRY-RUN-NOT-SENT',"mfaEnabled":True,"roles":[u['role']],"participantId":u.get('participantId')}
  if a.dry_run: results.append({'username':u['username'],'role':u['role'],'status':'DRY_RUN'}); continue
  req=urllib.request.Request(base+'/api/admin/users',data=json.dumps(body).encode(),method='POST',headers={'Authorization':'Bearer '+token,'Content-Type':'application/json','Accept':'application/json'})
  try:
   with urllib.request.urlopen(req,timeout=20) as r: payload=json.loads(r.read().decode() or '{}'); results.append({'username':u['username'],'role':u['role'],'status':r.status,'userId':payload.get('id'),'mfaSecretReturned':bool(payload.get('mfaEnrollmentSecret'))})
  except urllib.error.HTTPError as e: raise SystemExit(f'Provisioning failed for {u["username"]}: HTTP {e.code}')
 out={'schemaVersion':1,'apiBaseUrl':base,'dryRun':a.dry_run,'users':results,'passed':all(x['status'] in ('DRY_RUN',201) and (a.dry_run or x['mfaSecretReturned']) for x in results)}
 a.output.parent.mkdir(parents=True,exist_ok=True); a.output.write_text(json.dumps(out,indent=2)+'\n'); print(json.dumps({'users':len(results),'passed':out['passed']})); return 0 if out['passed'] else 1
if __name__=='__main__': raise SystemExit(main())
