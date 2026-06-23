#!/usr/bin/env python3
import argparse, json, os, socket, ssl, urllib.request
from pathlib import Path
ap=argparse.ArgumentParser(); ap.add_argument('--output',required=True); args=ap.parse_args(); checks=[]
def record(name,ok,detail): checks.append({'name':name,'ok':bool(ok),'detail':str(detail)})
url=os.getenv('UAT_HEALTH_URL','')
if url:
 try:
  with urllib.request.urlopen(url,timeout=10,context=ssl.create_default_context()) as r: record('application-health',200<=r.status<300,r.status)
 except Exception as e: record('application-health',False,e)
else: record('application-health',False,'UAT_HEALTH_URL missing')
for env,name in [('UAT_POSTGRES_HOST','postgres-primary'),('UAT_REPLICA_HOST','postgres-replica'),('UAT_KAFKA_HOST','kafka'),('UAT_VAULT_HOST','vault'),('UAT_OBJECT_STORAGE_HOST','object-storage'),('UAT_ALERTMANAGER_HOST','alertmanager')]:
 host=os.getenv(env,''); port=int(os.getenv(env.replace('_HOST','_PORT'),'443'))
 if not host: record(name,False,f'{env} missing'); continue
 try:
  with socket.create_connection((host,port),timeout=5): record(name,True,f'{host}:{port}')
 except Exception as e: record(name,False,e)
certified=all(x['ok'] for x in checks)
Path(args.output).write_text(json.dumps({'schemaVersion':1,'certified':certified,'checks':checks},indent=2,sort_keys=True)+'\n')
print(json.dumps({'certified':certified,'passed':sum(x['ok'] for x in checks),'total':len(checks)})); raise SystemExit(0 if certified else 1)
