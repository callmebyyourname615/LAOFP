#!/usr/bin/env python3
from __future__ import annotations
import argparse, datetime as dt, hashlib, json, pathlib, xml.etree.ElementTree as ET

def sha256(p:pathlib.Path)->str:
 h=hashlib.sha256(); h.update(p.read_bytes()); return h.hexdigest()

def main()->int:
 ap=argparse.ArgumentParser(); ap.add_argument('--root',default='target'); ap.add_argument('--output',required=True); ap.add_argument('--max-age-minutes',type=int,default=30); ap.add_argument('--minimum-tests',type=int,default=1); a=ap.parse_args()
 root=pathlib.Path(a.root); files=sorted(root.glob('surefire-reports/TEST-*.xml'))+sorted(root.glob('failsafe-reports/TEST-*.xml'))
 now=dt.datetime.now(dt.timezone.utc).timestamp(); totals={'tests':0,'failures':0,'errors':0,'skipped':0}; stale=[]; parsed=[]
 for p in files:
  age=(now-p.stat().st_mtime)/60
  if age>a.max_age_minutes: stale.append(str(p))
  try: node=ET.parse(p).getroot()
  except Exception as e: raise SystemExit(f'invalid XML {p}: {e}')
  row={k:int(float(node.attrib.get(k,0))) for k in totals}; parsed.append({'path':str(p),'sha256':sha256(p),'ageMinutes':round(age,2),**row})
  for k,v in row.items(): totals[k]+=v
 ok=bool(files) and not stale and totals['tests']>=a.minimum_tests and totals['failures']==0 and totals['errors']==0
 out={'schemaVersion':1,'files':parsed,'totals':totals,'staleReports':stale,'minimumTests':a.minimum_tests,'passed':ok}
 pathlib.Path(a.output).write_text(json.dumps(out,indent=2,sort_keys=True)+'\n')
 print(json.dumps({'passed':ok,'reports':len(files),**totals}))
 return 0 if ok else 2
if __name__=='__main__': raise SystemExit(main())
