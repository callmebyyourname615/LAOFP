#!/usr/bin/env python3
from __future__ import annotations
import argparse, datetime as dt, json, os, pathlib, socket, ssl, urllib.request

def check_url(name,url,timeout):
 try:
  ctx=ssl.create_default_context(); req=urllib.request.Request(url,headers={'User-Agent':'phase74-probe/1'})
  with urllib.request.urlopen(req,timeout=timeout,context=ctx) as r: return {'name':name,'target':url,'ok':200<=r.status<400,'status':r.status}
 except Exception as e: return {'name':name,'target':url,'ok':False,'error':str(e)}
def check_tcp(name,target,timeout):
 host,port=target.rsplit(':',1)
 try:
  with socket.create_connection((host,int(port)),timeout=timeout): return {'name':name,'target':target,'ok':True}
 except Exception as e: return {'name':name,'target':target,'ok':False,'error':str(e)}
def main()->int:
 ap=argparse.ArgumentParser(); ap.add_argument('--output',required=True); ap.add_argument('--timeout',type=float,default=5); a=ap.parse_args()
 required={'application':'PHASE74_APPLICATION_HEALTH_URL','vault':'PHASE74_VAULT_HEALTH_URL','objectStorage':'PHASE74_OBJECT_STORAGE_HEALTH_URL','alertmanager':'PHASE74_ALERTMANAGER_HEALTH_URL'}
 tcp={'postgresPrimary':'PHASE74_POSTGRES_PRIMARY','postgresReplica':'PHASE74_POSTGRES_REPLICA','kafka':'PHASE74_KAFKA_BOOTSTRAP'}
 checks=[]; missing=[]
 for name,key in required.items():
  value=os.getenv(key,''); missing += ([] if value else [key]); checks += ([check_url(name,value,a.timeout)] if value else [])
 for name,key in tcp.items():
  value=os.getenv(key,''); missing += ([] if value else [key]); checks += ([check_tcp(name,value,a.timeout)] if value else [])
 ok=not missing and all(x['ok'] for x in checks)
 out={'schemaVersion':1,'checkedAt':dt.datetime.now(dt.timezone.utc).isoformat().replace('+00:00','Z'),'missingEnvironment':missing,'checks':checks,'passed':ok}
 pathlib.Path(a.output).write_text(json.dumps(out,indent=2,sort_keys=True)+'\n')
 print(json.dumps({'passed':ok,'checks':len(checks),'missing':missing}))
 return 0 if ok else 2
if __name__=='__main__': raise SystemExit(main())
