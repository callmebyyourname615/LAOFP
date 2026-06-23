#!/usr/bin/env python3
from __future__ import annotations
import argparse, json, os, re, ssl, subprocess, sys, urllib.request
from datetime import datetime, timezone
from pathlib import Path
DIGEST=re.compile(r'^sha256:[0-9a-f]{64}$'); PLACE=re.compile(r'(?i)(replace|todo|tbd|change_me|example\.com)')
COMMANDS={
 'postgresPrimary':'PHASE65_POSTGRES_PRIMARY_CHECK_COMMAND', 'postgresReplica':'PHASE65_POSTGRES_REPLICA_CHECK_COMMAND',
 'kafka':'PHASE65_KAFKA_CHECK_COMMAND','vault':'PHASE65_VAULT_CHECK_COMMAND','objectStorage':'PHASE65_OBJECT_STORAGE_CHECK_COMMAND',
 'monitoring':'PHASE65_MONITORING_CHECK_COMMAND','timeSync':'PHASE65_TIME_SYNC_CHECK_COMMAND'}
def run(cmd):
 p=subprocess.run(cmd,shell=True,text=True,capture_output=True,timeout=60); return {'exitCode':p.returncode,'stdout':p.stdout[-4000:],'stderr':p.stderr[-4000:]}
def main():
 p=argparse.ArgumentParser(); p.add_argument('--output',type=Path,required=True); p.add_argument('--execute',action='store_true'); p.add_argument('--attestation',type=Path); a=p.parse_args(); errors=[]; checks={}
 app=os.getenv('PHASE65_UAT_HEALTH_URL',''); ad=os.getenv('APPLICATION_IMAGE_DIGEST',''); md=os.getenv('MIGRATION_IMAGE_DIGEST','')
 if not DIGEST.match(ad): errors.append('APPLICATION_IMAGE_DIGEST must be sha256:<64 hex>')
 if not DIGEST.match(md): errors.append('MIGRATION_IMAGE_DIGEST must be sha256:<64 hex>')
 if not app.startswith('https://'): errors.append('PHASE65_UAT_HEALTH_URL must use HTTPS')
 if a.execute and app.startswith('https://'):
  try:
   with urllib.request.urlopen(app,timeout=15,context=ssl.create_default_context()) as r: checks['applicationHealth']={'status':r.status,'passed':200<=r.status<300}
  except Exception as exc: checks['applicationHealth']={'passed':False,'error':str(exc)}; errors.append('application HTTPS health failed')
 for name,env in COMMANDS.items():
  cmd=os.getenv(env,'')
  if not cmd: errors.append(env+' is required')
  elif a.execute:
   checks[name]=run(cmd)
   if checks[name]['exitCode']!=0: errors.append(name+' check failed')
 att={}
 if a.attestation:
  att=json.loads(a.attestation.read_text())
  for k in ('tlsVerified','kafkaHealthy','vaultHealthy','objectStorageChecksumVerified','monitoringHealthy','timeSyncHealthy'):
   if att.get(k) is not True: errors.append(k+' must be true')
  if att.get('primaryInRecovery') is not False or att.get('replicaInRecovery') is not True: errors.append('primary/replica recovery roles are incorrect')
  if int(att.get('applicationReplicas',0))<4: errors.append('minimum four application replicas required')
  if float(att.get('stableHours',0))<24: errors.append('UAT must be stable for at least 24 hours')
  if float(att.get('replicaLagSeconds',999999))>5: errors.append('replica lag exceeds 5 seconds')
  for k in ('sreLead','qaLead','signedAt'):
   v=att.get(k); 
   if not isinstance(v,str) or not v.strip() or PLACE.search(v): errors.append(k+' missing/placeholder')
 out={'schemaVersion':1,'generatedAt':datetime.now(timezone.utc).isoformat().replace('+00:00','Z'),'executed':a.execute,'passed':not errors,'checks':checks,'attestation':att,'errors':errors}
 a.output.parent.mkdir(parents=True,exist_ok=True); a.output.write_text(json.dumps(out,indent=2)+'\n'); print('PASS' if not errors else '\n'.join('FAIL: '+x for x in errors)); return 0 if not errors else 1
if __name__=='__main__': raise SystemExit(main())
