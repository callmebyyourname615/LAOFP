#!/usr/bin/env python3
from __future__ import annotations
import argparse, json, tempfile, time, xml.etree.ElementTree as ET
from pathlib import Path

def collect(root:Path,fresh:int|None):
    files=sorted(list((root/'target/surefire-reports').glob('TEST-*.xml'))+list((root/'target/failsafe-reports').glob('TEST-*.xml')))
    totals={k:0 for k in ('tests','failures','errors','skipped')}; stale=[]
    now=time.time()
    for f in files:
        if fresh is not None and now-f.stat().st_mtime>fresh*60: stale.append(str(f))
        node=ET.parse(f).getroot()
        for k in totals: totals[k]+=int(float(node.attrib.get(k,0)))
    return files,totals,stale

def main():
    p=argparse.ArgumentParser(); p.add_argument('--root',default='.'); p.add_argument('--output'); p.add_argument('--require-fresh-minutes',type=int); p.add_argument('--self-test',action='store_true'); a=p.parse_args()
    if a.self_test:
        with tempfile.TemporaryDirectory() as d:
            q=Path(d)/'target/surefire-reports'; q.mkdir(parents=True); (q/'TEST-x.xml').write_text('<testsuite tests="2" failures="0" errors="0" skipped="1"/>')
            fs,t,s=collect(Path(d),60); assert len(fs)==1 and t=={'tests':2,'failures':0,'errors':0,'skipped':1} and not s
        return 0
    files,totals,stale=collect(Path(a.root),a.require_fresh_minutes)
    doc={'schemaVersion':1,'reportCount':len(files),'totals':totals,'staleReports':stale,'passed':bool(files) and not stale and totals['failures']==0 and totals['errors']==0}
    Path(a.output).write_text(json.dumps(doc,indent=2,sort_keys=True)+'\n')
    if not doc['passed']: raise SystemExit('missing/stale JUnit reports or test failures/errors detected')
    return 0
if __name__=='__main__': raise SystemExit(main())
