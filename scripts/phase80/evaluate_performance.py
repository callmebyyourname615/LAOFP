#!/usr/bin/env python3
import json,pathlib,sys
src=pathlib.Path(sys.argv[1]); out=pathlib.Path(sys.argv[2])
thresholds={'smoke':200,'sustained-2k-tps':300,'sustained-10k-tps':500}
results=[]; passed=True
for p in sorted(src.rglob('*.json')):
    try: d=json.load(open(p))
    except Exception: continue
    name=next((n for n in [*thresholds,'burst-20k-tps','soak-8h'] if p.name.startswith(n)),None)
    if not name: continue
    m=d.get('metrics',{})
    p95=m.get('http_req_duration',{}).get('values',{}).get('p(95)')
    er=m.get('http_req_failed',{}).get('values',{}).get('rate',0)
    ok=p95 is not None and (name not in thresholds or p95 < thresholds[name])
    if name=='burst-20k-tps': ok=ok and er < .005
    if name=='soak-8h': ok=ok and er < .001
    passed &= ok
    results.append({'scenario':name,'p95Ms':p95,'errorRate':er,'passed':ok,'source':str(p)})
required={'smoke','sustained-2k-tps','sustained-10k-tps','burst-20k-tps','soak-8h'}
passed &= required.issubset({r['scenario'] for r in results})
json.dump({'passed':passed,'results':results,'required':sorted(required)},open(out,'w'),indent=2,sort_keys=True)
