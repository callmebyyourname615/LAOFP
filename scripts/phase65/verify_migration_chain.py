#!/usr/bin/env python3
import argparse, hashlib, json, re, sys
from pathlib import Path
p=argparse.ArgumentParser(); p.add_argument('--output',type=Path,required=True); p.add_argument('--allow-missing-phase-ii',action='store_true'); a=p.parse_args()
root=Path('src/main/resources/db/migration'); files=sorted(root.glob('V*__*.sql')); versions={int(re.match(r'V(\d+)__',x.name).group(1)):x for x in files if re.match(r'V(\d+)__',x.name)}
expected=set(range(1,107))-{88,89,90,98,99}; missing=sorted(expected-set(versions)); extra=sorted(set(versions)-expected); duplicates=[]
external=set(range(91,97))|{102,103}; blocking=[v for v in missing if not (a.allow_missing_phase_ii and v in external)]
manifest=[{'version':v,'file':x.name,'sha256':hashlib.sha256(x.read_bytes()).hexdigest()} for v,x in sorted(versions.items())]
out={'schemaVersion':1,'latest':max(versions) if versions else None,'count':len(versions),'expectedCount':99,'missing':missing,'extra':extra,'reserved':[88,89,90,98,99],'allowMissingPhaseII':a.allow_missing_phase_ii,'passed':not blocking and not extra and len(versions)==99,'manifest':manifest}
# Delivery mode accepts only the authoritative Phase-II artifact gap, but never calls it certified.
if a.allow_missing_phase_ii and set(missing).issubset(external) and not extra: out['passed']=True; out['certified']=False; out['deliveryOnly']=True
else: out['certified']=out['passed']; out['deliveryOnly']=False
a.output.parent.mkdir(parents=True,exist_ok=True); a.output.write_text(json.dumps(out,indent=2)+'\n'); print(json.dumps({k:out[k] for k in ('latest','count','missing','passed','certified')}))
if not out['passed']: sys.exit(1)
