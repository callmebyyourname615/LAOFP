#!/usr/bin/env python3
from __future__ import annotations
import argparse,hashlib,json,subprocess
from datetime import datetime,timezone
from pathlib import Path
import jsonschema

def sha(p):
 h=hashlib.sha256()
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
 return h.hexdigest()
def main():
 p=argparse.ArgumentParser(); p.add_argument('--run-dir',required=True); p.add_argument('--schema',required=True); p.add_argument('--output',required=True); p.add_argument('--decision-output',required=True); p.add_argument('--approval'); a=p.parse_args()
 run=Path(a.run_dir).resolve(); phases=[]
 for code in [f'66{x}' for x in 'ABCDEFGHI']:
  rp=run/code/'result.json'
  phases.append(json.loads(rp.read_text()) if rp.exists() else {'phase':code,'status':'MISSING','message':'result missing'})
 statuses=[x['status'] for x in phases]; all_pass=all(x=='PASS' for x in statuses); all_ready=all(x in {'PASS','PREPARED'} for x in statuses)
 approval_ok=False; approval_digest=None
 if a.approval:
  ap=Path(a.approval); approval_digest=sha(ap); data=json.loads(ap.read_text()); approval_ok=data.get('approved') is True and bool(data.get('approver')) and bool(data.get('approvedAt'))
 decision='CERTIFIED' if all_pass and approval_ok else ('PREPARED' if all_ready and not all_pass else 'BLOCKED')
 artifacts=[]
 excluded={Path(a.output).resolve(),Path(a.decision_output).resolve()}
 for f in sorted(run.rglob('*')):
  if f.is_file() and not f.is_symlink() and f.resolve() not in excluded:
   artifacts.append({'path':f.relative_to(run).as_posix(),'size':f.stat().st_size,'sha256':sha(f)})
 try: commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=Path.cwd(),text=True,stderr=subprocess.DEVNULL).strip()
 except Exception: commit='unknown'
 doc={'schemaVersion':1,'generatedAt':datetime.now(timezone.utc).isoformat().replace('+00:00','Z'),'runId':run.name,'gitCommit':commit,'phases':phases,'artifacts':artifacts,'approval':{'present':bool(a.approval),'valid':approval_ok,'sha256':approval_digest},'decision':decision}
 schema=json.loads(Path(a.schema).read_text()); jsonschema.validate(doc,schema)
 Path(a.output).write_text(json.dumps(doc,indent=2,sort_keys=True)+'\n')
 decision_doc={'schemaVersion':1,'generatedAt':doc['generatedAt'],'runId':run.name,'decision':decision,'phaseStatuses':{x['phase']:x['status'] for x in phases},'approvalValid':approval_ok,'phase54EntryAllowed':decision=='CERTIFIED'}
 Path(a.decision_output).write_text(json.dumps(decision_doc,indent=2,sort_keys=True)+'\n')
 print(decision)
 return 0 if decision in {'PREPARED','CERTIFIED'} else 2
if __name__=='__main__':raise SystemExit(main())
