#!/usr/bin/env python3
from __future__ import annotations
import argparse, datetime as dt, hashlib, json, pathlib, re
PLACEHOLDER=re.compile(r'(TODO|REPLACE|EXAMPLE|UNKNOWN|TBD|CHANGEME)',re.I)
KINDS={'uat-infra','secret-rotation','smos-runtime','performance-baseline','performance-capacity','settlement','resilience-chaos','phase54-entry','production-infra','command-center','production-decision'}

def fail(msg): raise SystemExit(msg)
def main()->int:
 ap=argparse.ArgumentParser(); ap.add_argument('--kind',required=True,choices=sorted(KINDS)); ap.add_argument('--file',required=True); ap.add_argument('--output',required=True); ap.add_argument('--commit'); ap.add_argument('--application-digest'); ap.add_argument('--migration-digest'); a=ap.parse_args()
 p=pathlib.Path(a.file); d=json.loads(p.read_text())
 required=['schemaVersion','kind','status','commit','applicationImageDigest','migrationImageDigest','environment','operator','approvedAt','evidenceFiles','approvals']
 missing=[k for k in required if k not in d]
 errors=[]
 if missing: errors.append('missing keys: '+','.join(missing))
 if d.get('schemaVersion')!=1: errors.append('schemaVersion must be 1')
 if d.get('kind')!=a.kind: errors.append('kind mismatch')
 if d.get('status')!='PASS': errors.append('status must be PASS')
 if not re.fullmatch(r'[a-f0-9]{40}',str(d.get('commit',''))): errors.append('commit invalid')
 for k in ['applicationImageDigest','migrationImageDigest']:
  if not re.fullmatch(r'sha256:[a-f0-9]{64}',str(d.get(k,''))): errors.append(k+' invalid')
 if d.get('environment') not in {'uat','production'}: errors.append('environment invalid')
 if not isinstance(d.get('evidenceFiles'),list) or not d.get('evidenceFiles'): errors.append('evidenceFiles empty')
 if not isinstance(d.get('approvals'),list) or not d.get('approvals'): errors.append('approvals empty')
 text=json.dumps(d,sort_keys=True)
 if PLACEHOLDER.search(text): errors.append('placeholder value detected')
 if a.commit and d.get('commit')!=a.commit: errors.append('commit mismatch')
 if a.application_digest and d.get('applicationImageDigest')!=a.application_digest: errors.append('application digest mismatch')
 if a.migration_digest and d.get('migrationImageDigest')!=a.migration_digest: errors.append('migration digest mismatch')
 # Kind-specific fail-closed controls.
 if a.kind=='uat-infra':
  if d.get('allDependenciesHealthy') is not True: errors.append('allDependenciesHealthy must be true')
  if int(d.get('stabilityHours',0)) < 24: errors.append('stabilityHours must be >= 24')
  if int(d.get('applicationReplicas',0)) < 4: errors.append('applicationReplicas must be >= 4')
  if int(d.get('kafkaBrokers',0)) < 3: errors.append('kafkaBrokers must be >= 3')
 if a.kind=='secret-rotation':
  if len(d.get('rotatedSecretNames',[])) < 6: errors.append('six rotatedSecretNames required')
  for key in ('oldCredentialsDisabled','gitHistoryPurged','ciCachesInvalidated','serviceTokensRotated'):
   if d.get(key) is not True: errors.append(key+' must be true')
 if a.kind=='smos-runtime':
  if d.get('mfaMethod')!='TOTP': errors.append('mfaMethod must be TOTP')
  if int(d.get('initialOperators',0)) < 5: errors.append('initialOperators must be >= 5')
  for key in ('rbacPassed','makerCheckerPassed','participantIsolationPassed'):
   if d.get(key) is not True: errors.append(key+' must be true')
 if a.kind=='performance-baseline':
  for key in ('smokePassed','sustained2kPassed'):
   if d.get(key) is not True: errors.append(key+' must be true')
 if a.kind=='performance-capacity':
  for key in ('sustained10kPassed','burst20kPassed','soak8hPassed'):
   if d.get(key) is not True: errors.append(key+' must be true')
 if a.kind=='settlement':
  if int(d.get('inputTransactions',0)) < 500000: errors.append('inputTransactions must be >= 500000')
  if d.get('balanced') is not True: errors.append('balanced must be true')
  if float(d.get('reconciliationDifference',1)) != 0: errors.append('reconciliationDifference must be zero')
 if a.kind=='resilience-chaos':
  if float(d.get('rpoMinutes',999)) > 5: errors.append('RPO exceeds 5 minutes')
  if float(d.get('rtoMinutes',999)) > 30: errors.append('RTO exceeds 30 minutes')
  for key in ('zeroTransactionLoss','failbackPassed','alertsRouted'):
   if d.get(key) is not True: errors.append(key+' must be true')
  if int(d.get('chaosScenariosPassed',0)) < 8: errors.append('eight real chaos scenarios required')
 if a.kind=='phase54-entry':
  if d.get('decision')!='GO': errors.append('Phase 54 entry decision must be GO')
  needed={'Engineering Lead','QA Lead','Security Lead','SRE Lead','Product Business Owner','Change Manager'}
  roles={x.get('role') for x in d.get('approvals',[]) if isinstance(x,dict) and x.get('approved') is True}
  if not needed.issubset(roles): errors.append('all six Phase 54 entry approvals required')
 evidence=[]
 for rel in d.get('evidenceFiles',[]):
  ep=pathlib.Path(rel)
  if not ep.is_absolute(): ep=(p.parent/ep).resolve()
  if not ep.is_file() or ep.is_symlink(): errors.append(f'evidence missing or symlink: {rel}'); continue
  evidence.append({'path':str(ep),'size':ep.stat().st_size,'sha256':hashlib.sha256(ep.read_bytes()).hexdigest()})
 out={'schemaVersion':1,'kind':a.kind,'source':str(p.resolve()),'passed':not errors,'errors':errors,'evidence':evidence,'attestationSha256':hashlib.sha256(p.read_bytes()).hexdigest()}
 pathlib.Path(a.output).write_text(json.dumps(out,indent=2,sort_keys=True)+'\n')
 print(json.dumps({'passed':not errors,'kind':a.kind,'evidence':len(evidence),'errors':errors}))
 return 0 if not errors else 2
if __name__=='__main__': raise SystemExit(main())
