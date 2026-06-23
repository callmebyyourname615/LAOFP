#!/usr/bin/env python3
from __future__ import annotations
import argparse, hashlib, json, os, re, subprocess, sys
from datetime import date, datetime, timezone
from pathlib import Path
P=re.compile(r'(?i)(replace|todo|tbd|change_me)'); DIGEST=re.compile(r'^sha256:[0-9a-f]{64}$')
def text(d,k,e):
 v=d.get(k)
 if not isinstance(v,str) or not v.strip() or P.search(v): e.append(k+' missing/placeholder')
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
 ap=argparse.ArgumentParser(); ap.add_argument('--decisions',type=Path,required=True); ap.add_argument('--handoff',type=Path,required=True); ap.add_argument('--output',type=Path,required=True); a=ap.parse_args(); e=[]
 decisions=json.loads(a.decisions.read_text()); handoff=json.loads(a.handoff.read_text())
 if decisions.get('mfaMethod')!='TOTP': e.append('mfaMethod must be TOTP for current implementation')
 if decisions.get('performanceSlaHardGate') is not True: e.append('performance SLA must be a hard gate')
 try:
  if int(decisions.get('approvedDrRpoSeconds')) not in (0,300): e.append('approvedDrRpoSeconds must be 0 or 300')
 except Exception:e.append('approvedDrRpoSeconds invalid')
 if decisions.get('smosRolePolicy') not in ('BRD_8_FIXED_ROLES','BRD_8_PLUS_APPROVED_CUSTOM'):e.append('invalid SMOS role policy')
 if decisions.get('promotionLaunch') not in ('GO_LIVE_ENABLED','DEFERRED_DISABLED'):e.append('invalid promotion launch decision')
 if decisions.get('multiRegionMode') not in ('ACTIVE_STANDBY','ACTIVE_ACTIVE'):e.append('invalid multi-region mode')
 try:
  if date.fromisoformat(decisions.get('goLiveDate','')) < date.today():e.append('goLiveDate is in the past')
 except Exception:e.append('goLiveDate invalid')
 for k in ('productOwner','operationsLead','securityLead','signedAt'):text(decisions,k,e)
 for k in ('engineeringLead','qaLead','securityLead','sreLead','productOwner','changeManager','signedAt'):text(handoff,k,e)
 if handoff.get('approved') is not True:e.append('handoff approved must be true')
 bundle=Path(handoff.get('phase64Bundle',''))
 files=[]
 if not bundle.is_dir():e.append('phase64 signed bundle directory missing')
 else:
  for p in sorted(bundle.rglob('*')):
   if p.is_file() and p.stat().st_size>0:files.append({'path':p.relative_to(bundle).as_posix(),'bytes':p.stat().st_size,'sha256':sha(p)})
  if not files:e.append('phase64 bundle is empty')
 ad=os.getenv('APPLICATION_IMAGE_DIGEST',''); md=os.getenv('MIGRATION_IMAGE_DIGEST','')
 if not DIGEST.match(ad):e.append('APPLICATION_IMAGE_DIGEST invalid')
 if not DIGEST.match(md):e.append('MIGRATION_IMAGE_DIGEST invalid')
 try:commit=subprocess.check_output(['git','rev-parse','HEAD'],text=True,timeout=5).strip()
 except Exception:commit='unknown'
 out={'schemaVersion':1,'generatedAt':datetime.now(timezone.utc).isoformat().replace('+00:00','Z'),'passed':not e,'commit':commit,'applicationImageDigest':ad,'migrationImageDigest':md,'decisions':decisions,'approvals':handoff,'phase64Files':files,'errors':e}
 a.output.parent.mkdir(parents=True,exist_ok=True); a.output.write_text(json.dumps(out,indent=2)+'\n'); print('PASS' if not e else '\n'.join('FAIL: '+x for x in e)); return 0 if not e else 1
if __name__=='__main__':raise SystemExit(main())
