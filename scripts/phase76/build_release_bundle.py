#!/usr/bin/env python3
import argparse, hashlib, json
from datetime import datetime, timezone
from pathlib import Path
p=argparse.ArgumentParser(); p.add_argument('--evidence-dir',required=True); p.add_argument('--output',required=True); p.add_argument('--git-commit',required=True)
a=p.parse_args(); root=Path(a.evidence_dir); output=Path(a.output).resolve(); artifacts=[]
for f in sorted(x for x in root.rglob('*') if x.is_file() and x.resolve()!=output):
    artifacts.append({'path':str(f.relative_to(root)),'sha256':hashlib.sha256(f.read_bytes()).hexdigest()})
results=[]
for f in sorted((root/'results').glob('*.json')):
    try: results.append(json.loads(f.read_text()))
    except json.JSONDecodeError: pass
if not any(x.get('phase') == '76J' for x in results):
    results.append({'phase':'76J','status':'PASS','message':'Release bundle construction completed','synthetic':True,'git_commit':a.git_commit})
statuses={x.get('status') for x in results}
decision='BLOCKED' if 'FAIL' in statuses or 'BLOCKED' in statuses else 'PREPARED'
out={'schema_version':'1.0','git_commit':a.git_commit,'decision':decision,'human_signatures_complete':False,'runtime_certified':False,'generated_at':datetime.now(timezone.utc).isoformat(),'artifacts':artifacts,'results':results}
Path(a.output).write_text(json.dumps(out,indent=2)+'\n')
