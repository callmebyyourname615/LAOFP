#!/usr/bin/env python3
import json, os, ssl, subprocess, sys, urllib.request
from datetime import datetime, timezone
base=os.environ.get('UAT_BASE_URL','').rstrip('/'); errors=[]; checks={}
if not base.startswith('https://'): errors.append('UAT_BASE_URL must use https://')
else:
 req=urllib.request.Request(base+'/actuator/health')
 token=os.environ.get('OPS_HEALTH_TOKEN','')
 if token: req.add_header('X-Ops-Health-Token',token)
 try:
  with urllib.request.urlopen(req,timeout=15,context=ssl.create_default_context()) as r:
   payload=json.loads(r.read().decode()); checks['applicationHealth']=payload.get('status')=='UP';
   if not checks['applicationHealth']: errors.append(f'application status is {payload.get("status")}')
 except Exception as e: checks['applicationHealth']=False; errors.append(f'application health probe failed: {e}')
for key,env in {
 'postgresPrimary':'PHASE68_POSTGRES_PRIMARY_CHECK_CMD',
 'postgresReplica':'PHASE68_POSTGRES_REPLICA_CHECK_CMD',
 'kafka':'PHASE68_KAFKA_CHECK_CMD',
 'vault':'PHASE68_VAULT_CHECK_CMD',
 'objectStorage':'PHASE68_OBJECT_STORAGE_CHECK_CMD',
 'monitoring':'PHASE68_MONITORING_CHECK_CMD',
}.items():
 cmd=os.environ.get(env,'')
 if not cmd: checks[key]=False; errors.append(f'{env} is required'); continue
 cp=subprocess.run(cmd,shell=True,text=True,capture_output=True,timeout=120)
 checks[key]=cp.returncode==0
 if cp.returncode: errors.append(f'{key} check failed: {cp.stderr[-300:]}')
out={'schemaVersion':1,'checkedAt':datetime.now(timezone.utc).isoformat().replace('+00:00','Z'),'baseUrl':base,'checks':checks,'passed':not errors,'errors':errors}
path=os.environ.get('PHASE68_UAT_PROBE_OUTPUT')
if path:
 from pathlib import Path
 p=Path(path); p.parent.mkdir(parents=True,exist_ok=True); p.write_text(json.dumps(out,indent=2,sort_keys=True)+'\n')
print(json.dumps(out,indent=2,sort_keys=True)); sys.exit(0 if not errors else 1)
