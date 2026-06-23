#!/usr/bin/env python3
import argparse,json,re,sys
from pathlib import Path
NAMES={'POSTGRES_PASSWORD','REPLICATION_PASSWORD','DB_APP_PASSWORD','FLYWAY_PASSWORD','ARCHIVE_POSTGRES_PASSWORD','MINIO_ROOT_PASSWORD'}; P=re.compile(r'(?i)(replace|todo|tbd|change_me)')
p=argparse.ArgumentParser(); p.add_argument('--attestation',type=Path,required=True); p.add_argument('--output',type=Path,required=True); a=p.parse_args(); d=json.loads(a.attestation.read_text()); e=[]
if set((d.get('credentials') or {}))!=NAMES: e.append('credential set must contain exactly six required names')
for n in NAMES:
 x=(d.get('credentials') or {}).get(n,{})
 if x.get('rotated') is not True or x.get('oldDisabled') is not True: e.append(n+' not fully rotated/disabled')
for k in ('historyPurgeExecuted','forcePushCompleted','actionsCachesInvalidated','serviceAccountTokensRotated','teamRecloneConfirmed','gitleaksPassed'):
 if d.get(k) is not True:e.append(k+' must be true')
for k in ('vaultPath','repositoryCoordinator','securityLead','signedAt'):
 v=d.get(k); 
 if not isinstance(v,str) or not v.strip() or P.search(v): e.append(k+' missing/placeholder')
out={'schemaVersion':1,'passed':not e,'attestation':d,'errors':e}; a.output.parent.mkdir(parents=True,exist_ok=True); a.output.write_text(json.dumps(out,indent=2)+'\n'); print('PASS' if not e else '\n'.join('FAIL: '+x for x in e)); sys.exit(0 if not e else 1)
