#!/usr/bin/env python3
import argparse, json, os
from datetime import datetime, timezone
from pathlib import Path
p=argparse.ArgumentParser()
p.add_argument('--phase', required=True); p.add_argument('--status', required=True)
p.add_argument('--message', required=True); p.add_argument('--output', required=True)
p.add_argument('--synthetic', action='store_true'); p.add_argument('--details', default='{}')
a=p.parse_args()
try: details=json.loads(a.details)
except json.JSONDecodeError as e: raise SystemExit(f'invalid --details JSON: {e}')
out={"phase":a.phase,"status":a.status,"message":a.message,"synthetic":a.synthetic,
     "run_id":os.environ.get('RUN_ID','local'),"git_commit":os.environ.get('GIT_COMMIT','unknown'),
     "generated_at":datetime.now(timezone.utc).isoformat(),"details":details}
path=Path(a.output); path.parent.mkdir(parents=True, exist_ok=True); path.write_text(json.dumps(out,indent=2)+"\n")
print(json.dumps(out))
