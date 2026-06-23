#!/usr/bin/env python3
from __future__ import annotations
import argparse, json, os, socket, ssl, subprocess, urllib.request, datetime
from datetime import datetime, timezone
from pathlib import Path
import yaml

def env_expand(value):
    if not isinstance(value,str): return value
    if value.startswith('${') and value.endswith('}'):
        key=value[2:-1]; return os.environ.get(key,'')
    return value

def tcp(host,port,timeout):
    with socket.create_connection((host,int(port)),timeout=timeout): pass

def http(url,timeout,expected):
    req=urllib.request.Request(url,headers={'User-Agent':'phase66-probe/1'})
    with urllib.request.urlopen(req,timeout=timeout,context=ssl.create_default_context()) as r:
        if r.status not in expected: raise RuntimeError(f'HTTP {r.status}')

def main():
    p=argparse.ArgumentParser(); p.add_argument('--config',required=True); p.add_argument('--output',required=True); p.add_argument('--contract-only',action='store_true')
    a=p.parse_args(); cfg=yaml.safe_load(Path(a.config).read_text()); results=[]
    if cfg.get('schemaVersion')!=1: raise SystemExit('unsupported dependency contract')
    for item in cfg.get('dependencies',[]):
        row={'id':item['id'],'type':item['type'],'required':bool(item.get('required',True)),'status':'NOT_RUN','detail':'contract validated'}
        try:
            if not a.contract_only:
                timeout=float(item.get('timeoutSeconds',5)); kind=item['type']
                if kind=='http': http(env_expand(item['url']),timeout,item.get('expectedStatus',[200]))
                elif kind=='tcp': tcp(env_expand(item['host']),env_expand(str(item['port'])),timeout)
                elif kind=='command':
                    cmd=[env_expand(x) for x in item['command']]
                    subprocess.run(cmd,check=True,timeout=timeout,capture_output=True,text=True)
                elif kind=='postgres-role':
                    url=os.environ.get(item['urlEnv'],''); user=os.environ.get(item['userEnv'],''); password=os.environ.get(item['passwordEnv'],'')
                    if not all((url,user,password)): raise RuntimeError('postgres role probe environment is incomplete')
                    proc=subprocess.run(['psql',url,'-U',user,'-AtX','-v','ON_ERROR_STOP=1','-c','SELECT pg_is_in_recovery()'],env={**os.environ,'PGPASSWORD':password},text=True,capture_output=True,timeout=timeout,check=True)
                    expected='t' if item['expectRecovery'] else 'f'
                    if proc.stdout.strip()!=expected: raise RuntimeError(f'expected pg_is_in_recovery={expected}, got {proc.stdout.strip()}')
                elif kind=='tls':
                    host=env_expand(item['host']); port=int(env_expand(str(item['port'])))
                    ctx=ssl.create_default_context()
                    with socket.create_connection((host,port),timeout=timeout) as sock:
                        with ctx.wrap_socket(sock,server_hostname=host) as tls_sock: cert=tls_sock.getpeercert()
                    expires=datetime.datetime.strptime(cert['notAfter'],'%b %d %H:%M:%S %Y %Z').replace(tzinfo=datetime.timezone.utc)
                    days=(expires-datetime.datetime.now(datetime.timezone.utc)).total_seconds()/86400
                    if days < float(item.get('minDaysRemaining',30)): raise RuntimeError(f'TLS certificate expires in {days:.1f} days')
                else: raise ValueError(f'unsupported probe type {kind}')
                row.update(status='PASS',detail='probe succeeded')
        except Exception as e: row.update(status='FAIL',detail=str(e)[:500])
        results.append(row)
    ok=all((not r['required']) or r['status'] in ({'NOT_RUN'} if a.contract_only else {'PASS'}) for r in results)
    doc={'schemaVersion':1,'generatedAt':datetime.now(timezone.utc).isoformat().replace('+00:00','Z'),'contractOnly':a.contract_only,'results':results,'passed':ok}
    out=Path(a.output); out.parent.mkdir(parents=True,exist_ok=True); out.write_text(json.dumps(doc,indent=2,sort_keys=True)+'\n')
    return 0 if ok else 2
if __name__=='__main__': raise SystemExit(main())
