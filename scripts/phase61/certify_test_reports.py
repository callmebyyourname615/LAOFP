#!/usr/bin/env python3
from __future__ import annotations
import argparse, json, subprocess, sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

def main() -> int:
    p=argparse.ArgumentParser(); p.add_argument('--root',default='.'); p.add_argument('--minimum-tests',type=int,default=1); p.add_argument('--output',type=Path,required=True); a=p.parse_args()
    temporary=a.output.with_suffix('.raw.json')
    cmd=[sys.executable,str(ROOT/'scripts/phase60/summarize_test_reports.py'),'--root',a.root,'--output',str(temporary)]
    completed=subprocess.run(cmd,cwd=ROOT,check=False,text=True,capture_output=True)
    errors=[]
    if not temporary.is_file(): errors.append('test summarizer did not produce output'); data={}
    else: data=json.loads(temporary.read_text(encoding='utf-8'))
    totals=data.get('totals') or {}
    if completed.returncode != 0: errors.append('Surefire/Failsafe reports are missing, stale, malformed or failing')
    if int(totals.get('tests',0)) < a.minimum_tests: errors.append(f"tests {totals.get('tests',0)} below minimum {a.minimum_tests}")
    if int(totals.get('failures',0)) != 0 or int(totals.get('errors',0)) != 0: errors.append('test failures/errors must be zero')
    if data.get('currentValidation') is not True: errors.append('reports are not current for this source revision')
    document={'schemaVersion':1,'generatedAt':datetime.now(timezone.utc).isoformat().replace('+00:00','Z'),'passed':not errors,'minimumTests':a.minimum_tests,'summary':data,'errors':errors}
    a.output.parent.mkdir(parents=True,exist_ok=True); a.output.write_text(json.dumps(document,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    temporary.unlink(missing_ok=True)
    print(f"Phase 61A test certification: {'PASS' if not errors else 'FAIL'}")
    for e in errors: print('  ERROR:',e)
    return 0 if not errors else 1
if __name__=='__main__': raise SystemExit(main())
