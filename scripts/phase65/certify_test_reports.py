#!/usr/bin/env python3
import argparse, hashlib, json, subprocess, sys, time
from pathlib import Path
import xml.etree.ElementTree as ET
p=argparse.ArgumentParser(); p.add_argument('--target',type=Path,default=Path('target')); p.add_argument('--output',type=Path,required=True); p.add_argument('--max-age-seconds',type=int,default=3600); p.add_argument('--minimum-tests',type=int,default=400); a=p.parse_args()
files=sorted(a.target.glob('surefire-reports/TEST-*.xml'))+sorted(a.target.glob('failsafe-reports/TEST-*.xml'))
if not files: raise SystemExit('No Surefire/Failsafe XML reports')
if time.time()-max(x.stat().st_mtime for x in files)>a.max_age_seconds: raise SystemExit('Test reports are stale')
t=f=e=s=0; h=hashlib.sha256(); suites=[]
for x in files:
    b=x.read_bytes(); h.update(b); n=ET.fromstring(b); values={k:int(float(n.attrib.get(k,0))) for k in ('tests','failures','errors','skipped')}; t+=values['tests']; f+=values['failures']; e+=values['errors']; s+=values['skipped']; suites.append({'file':x.as_posix(),**values})
try: commit=subprocess.check_output(['git','rev-parse','HEAD'],text=True,timeout=5).strip()
except Exception: commit='unknown'
out={'schemaVersion':1,'commit':commit,'tests':t,'failures':f,'errors':e,'skipped':s,'reportCount':len(files),'reportsSha256':h.hexdigest(),'passed':t>=a.minimum_tests and f==0 and e==0,'suites':suites}
a.output.parent.mkdir(parents=True,exist_ok=True); a.output.write_text(json.dumps(out,indent=2)+'\n'); print(json.dumps({k:out[k] for k in ('tests','failures','errors','skipped','passed')}))
if not out['passed']: sys.exit(1)
