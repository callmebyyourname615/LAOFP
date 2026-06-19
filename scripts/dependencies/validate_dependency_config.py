#!/usr/bin/env python3
import pathlib,sys,urllib.parse
import yaml

def main(path):
    doc=yaml.safe_load(pathlib.Path(path).read_text(encoding='utf-8'));items=doc.get('dependencies') or []
    if not items:raise SystemExit('dependencies are required')
    seen=set()
    for i,item in enumerate(items):
        for key in ('code','name','type','endpoint','criticality','sla'):
            if item.get(key) in (None,''):raise SystemExit(f'dependencies[{i}].{key} is required')
        if item['code'] in seen:raise SystemExit(f'duplicate dependency code {item["code"]}')
        seen.add(item['code']);p=urllib.parse.urlparse(item['endpoint'])
        if p.scheme!='https' or not p.hostname or p.username or p.password or p.fragment:raise SystemExit('endpoint must be credential-free HTTPS without fragment')
        if item['criticality'] not in {'CRITICAL','HIGH','MEDIUM','LOW'}:raise SystemExit('invalid criticality')
        sla=item['sla']
        if not 0<float(sla.get('availability_target',0))<=1:raise SystemExit('availability_target must be 0..1')
        for key in ('latency_p95_ms','failure_threshold','recovery_success_threshold','open_seconds'):
            if not isinstance(sla.get(key),int) or sla[key]<=0:raise SystemExit(f'sla.{key} must be positive integer')
    print(f'validated {len(items)} dependency definitions')
if __name__=='__main__':
    if len(sys.argv)!=2:raise SystemExit('usage: validate_dependency_config.py CONFIG.yaml')
    main(sys.argv[1])
