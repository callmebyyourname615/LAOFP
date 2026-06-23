#!/usr/bin/env python3
import argparse, hashlib, json, subprocess, sys, time
from pathlib import Path
import xml.etree.ElementTree as ET
p=argparse.ArgumentParser(); p.add_argument('--reports',default='target'); p.add_argument('--output',required=True); a=p.parse_args()
root=Path(a.reports); files=list(root.glob('surefire-reports/TEST-*.xml'))+list(root.glob('failsafe-reports/TEST-*.xml'))
if not files: raise SystemExit('No Surefire/Failsafe XML reports found')
latest=max(f.stat().st_mtime for f in files)
if time.time()-latest>3600: raise SystemExit('Test reports are stale (>1 hour)')
tests=failures=errors=skipped=0; digest=hashlib.sha256()
for f in sorted(files):
    digest.update(f.read_bytes()); node=ET.parse(f).getroot()
    tests+=int(float(node.attrib.get('tests',0))); failures+=int(float(node.attrib.get('failures',0)))
    errors+=int(float(node.attrib.get('errors',0))); skipped+=int(float(node.attrib.get('skipped',0)))
try: commit=subprocess.check_output(['git','rev-parse','HEAD'],text=True,timeout=5).strip()
except Exception: commit='unknown'
result={'commit':commit,'tests':tests,'failures':failures,'errors':errors,'skipped':skipped,'reportCount':len(files),'sha256':digest.hexdigest()}
Path(a.output).parent.mkdir(parents=True,exist_ok=True); Path(a.output).write_text(json.dumps(result,indent=2))
print(json.dumps(result))
if failures or errors: sys.exit(1)
