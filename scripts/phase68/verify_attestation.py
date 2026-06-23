#!/usr/bin/env python3
import argparse, json, re, sys
from pathlib import Path
p=argparse.ArgumentParser(); p.add_argument('--file',type=Path,required=True); p.add_argument('--kind',choices=['uat','secret','smos','performance','resilience','kickoff'],required=True); p.add_argument('--output',type=Path); a=p.parse_args(); errors=[]
try: d=json.loads(a.file.read_text())
except Exception as e: print(f'FAIL: {e}',file=sys.stderr); sys.exit(1)
common=['commit','applicationImageDigest','migrationImageDigest','approvedBy','approvedAt']
required={
 'uat':common+['environment','stableHours','applicationReplicas','postgresPrimaryHealthy','postgresReplicaHealthy','kafkaHealthy','vaultHealthy','objectStorageHealthy','monitoringHealthy','tlsVerified'],
 'secret':common+['credentialsRotated','oldCredentialsDisabled','vaultUpdated','historyPurged','forcePushCompleted','ciCachesInvalidated','serviceTokensRotated','teamRecloneConfirmed','gitleaksPassed'],
 'smos':common+['initialAdminsSeeded','mfaEnforced','rbacVerified','makerCheckerVerified','participantIsolationVerified','adminEndpointAuditPassed'],
 'performance':common+['smokePassed','sustained2kPassed','sustained10kPassed','burst20kPassed','soak8hPassed','settlement500kPassed','capacityPlanApproved'],
 'resilience':common+['backupVerified','pitrPassed','postgresFailoverPassed','postgresFailbackPassed','kafkaFailurePassed','networkPartitionPassed','objectStorageFailurePassed','vaultFailoverPassed','alertRoutingPassed','transactionLossCount','rpoSeconds','rtoSeconds'],
 'kickoff':common+['phase64Passed','phase65Passed','phase66Passed','p0Blockers','releaseTag','sbomDigest','cosignVerified','provenanceVerified','changeRequestId'],
}[a.kind]
for k in required:
 if k not in d: errors.append(f'missing {k}')
for k,v in d.items():
 if k.endswith(('Passed','Verified','Completed','Rotated','Disabled','Updated','Purged','Invalidated','Confirmed','Seeded','Enforced','Approved','Healthy')) and isinstance(v,bool) and not v: errors.append(f'{k} must be true')
for k in ('applicationImageDigest','migrationImageDigest','sbomDigest'):
 if k in d and d[k] and not re.fullmatch(r'sha256:[0-9a-f]{64}',str(d[k])): errors.append(f'{k} must be sha256:<64 hex>')
if a.kind=='uat':
 if d.get('environment')!='uat': errors.append('environment must be uat')
 if int(d.get('stableHours',0))<24: errors.append('stableHours must be >= 24')
 if int(d.get('applicationReplicas',0))<4: errors.append('applicationReplicas must be >= 4')
if a.kind=='resilience':
 if int(d.get('transactionLossCount',1))!=0: errors.append('transactionLossCount must be 0')
 if float(d.get('rpoSeconds',999999))>300: errors.append('rpoSeconds must be <= 300')
 if float(d.get('rtoSeconds',999999))>1800: errors.append('rtoSeconds must be <= 1800')
if a.kind=='kickoff' and int(d.get('p0Blockers',1))!=0: errors.append('p0Blockers must be 0')
result={'schemaVersion':1,'kind':a.kind,'passed':not errors,'errors':errors,'source':a.file.as_posix()}
if a.output: a.output.parent.mkdir(parents=True,exist_ok=True); a.output.write_text(json.dumps(result,indent=2,sort_keys=True)+'\n')
print(json.dumps(result,indent=2,sort_keys=True)); sys.exit(0 if not errors else 1)
