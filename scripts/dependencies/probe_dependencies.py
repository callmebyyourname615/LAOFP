#!/usr/bin/env python3
import argparse, hashlib, ipaddress, json, socket, ssl, time, urllib.error, urllib.parse, urllib.request
from pathlib import Path
import yaml

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*args,**kwargs): raise urllib.error.HTTPError(args[0],args[1],"redirect blocked",args[3],args[4])

def validate_url(url):
    parsed=urllib.parse.urlparse(url)
    if parsed.scheme!='https' or not parsed.hostname or parsed.username or parsed.password or parsed.fragment: raise ValueError('dependency endpoint must be plain HTTPS without credentials/fragment')
    addresses=socket.getaddrinfo(parsed.hostname,parsed.port or 443,type=socket.SOCK_STREAM)
    for item in addresses:
        ip=ipaddress.ip_address(item[4][0])
        if ip.is_private or ip.is_loopback or ip.is_link_local or ip.is_multicast or ip.is_reserved or ip.is_unspecified: raise ValueError(f'unsafe resolved address {ip}')

def probe(item):
    url=item['endpoint']; validate_url(url); timeout=int(item.get('timeout_seconds',5)); expected=int(item.get('expected_status',200))
    start=time.monotonic(); status=0; error=None
    try:
        opener=urllib.request.build_opener(urllib.request.HTTPSHandler(context=ssl.create_default_context()),NoRedirect())
        with opener.open(urllib.request.Request(url,method='HEAD',headers={'User-Agent':'switching-dependency-probe/1'}),timeout=timeout) as response: status=response.status
    except Exception as exc: error=type(exc).__name__+': '+str(exc)[:300]
    latency=int((time.monotonic()-start)*1000); success=status==expected and error is None
    canonical=f'{item["code"]}|{success}|{status}|{latency}|{error or ""}'
    return {'dependency_code':item['code'],'success':success,'status':status,'latency_ms':latency,'error':error,'evidence_sha256':hashlib.sha256(canonical.encode()).hexdigest()}

def main(config,output):
    doc=yaml.safe_load(Path(config).read_text(encoding='utf-8')); deps=doc.get('dependencies') or []
    if not deps: raise SystemExit('dependencies are required')
    seen=set(); results=[]
    for item in deps:
        for key in ('code','name','type','endpoint','criticality','sla'):
            if item.get(key) in (None,''): raise SystemExit(f'{key} is required')
        if item['code'] in seen: raise SystemExit(f'duplicate dependency code {item["code"]}')
        seen.add(item['code']); results.append(probe(item))
    Path(output).write_text(json.dumps({'results':results},sort_keys=True,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'total':len(results),'failed':sum(not r['success'] for r in results)}))
    return 0 if all(r['success'] for r in results) else 2
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('config');p.add_argument('output');args=p.parse_args();raise SystemExit(main(args.config,args.output))
