#!/usr/bin/env python3
from __future__ import annotations
import argparse, json, os
from datetime import datetime, timezone
from pathlib import Path

p=argparse.ArgumentParser()
p.add_argument('--phase', required=True)
p.add_argument('--status', required=True, choices=['PASS','FAIL','BLOCKED','PREPARED'])
p.add_argument('--message', required=True)
p.add_argument('--mode', required=True, choices=['preflight','full'])
p.add_argument('--output', required=True)
p.add_argument('--evidence', action='append', default=[])
p.add_argument('--metric', action='append', default=[])
a=p.parse_args()
metrics={}
for item in a.metric:
    key, sep, value=item.partition('=')
    if not sep:
        raise SystemExit(f'invalid metric {item!r}; expected key=value')
    try: metrics[key]=json.loads(value)
    except json.JSONDecodeError: metrics[key]=value
payload={
    'schemaVersion':1,
    'phase':a.phase,
    'status':a.status,
    'mode':a.mode,
    'message':a.message,
    'timestamp':datetime.now(timezone.utc).isoformat(),
    'gitCommit':os.environ.get('PHASE69_GIT_SHA','unknown'),
    'evidence':a.evidence,
    'metrics':metrics,
}
out=Path(a.output); out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(json.dumps(payload, indent=2, sort_keys=True)+'\n', encoding='utf-8')
