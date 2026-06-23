#!/usr/bin/env python3
import argparse, datetime, hashlib, json, xml.etree.ElementTree as ET
from pathlib import Path
root=Path(__file__).resolve().parents[2]
ap=argparse.ArgumentParser(); ap.add_argument('--output',required=True); ap.add_argument('--max-age-minutes',type=int,default=120); ap.add_argument('--minimum-tests',type=int,default=400); args=ap.parse_args()
files=sorted((root/'target').glob('*-reports/TEST-*.xml'))
now=datetime.datetime.now(datetime.timezone.utc).timestamp(); tests=failures=errors=skipped=0; stale=[]; digests={}; parse_errors=[]
for p in files:
    try:
        r=ET.parse(p).getroot(); tests+=int(float(r.attrib.get('tests',0))); failures+=int(float(r.attrib.get('failures',0))); errors+=int(float(r.attrib.get('errors',0))); skipped+=int(float(r.attrib.get('skipped',0)))
        if now-p.stat().st_mtime > args.max_age_minutes*60: stale.append(str(p.relative_to(root)))
        digests[str(p.relative_to(root))]=hashlib.sha256(p.read_bytes()).hexdigest()
    except Exception as e: parse_errors.append(f'{p}: {e}')
certified=bool(files) and tests>=args.minimum_tests and failures==0 and errors==0 and not stale and not parse_errors
out={'schemaVersion':1,'certified':certified,'reports':len(files),'tests':tests,'failures':failures,'errors':errors,'skipped':skipped,'staleReports':stale,'parseErrors':parse_errors,'sha256':digests}
Path(args.output).write_text(json.dumps(out,indent=2,sort_keys=True)+'\n')
print(json.dumps({k:out[k] for k in ('certified','reports','tests','failures','errors','skipped')},sort_keys=True))
raise SystemExit(0 if certified else 1)
