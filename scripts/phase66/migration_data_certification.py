#!/usr/bin/env python3
from __future__ import annotations
import argparse, hashlib, json, re
from datetime import datetime, timezone
from pathlib import Path

def main():
    p=argparse.ArgumentParser(); p.add_argument('--root',default='.'); p.add_argument('--output',required=True); p.add_argument('--database-if-configured',action='store_true'); a=p.parse_args()
    root=Path(a.root); mdir=root/'src/main/resources/db/migration'; rx=re.compile(r'^V([0-9]+(?:\.[0-9]+)?)__(.+)\.sql$')
    rows=[]; versions={}; invalid=[]
    for f in sorted(mdir.glob('V*.sql')):
        m=rx.match(f.name)
        if not m: invalid.append(f.name); continue
        v=m.group(1); versions.setdefault(v,[]).append(f.name)
        rows.append({'version':v,'file':f.name,'sha256':hashlib.sha256(f.read_bytes()).hexdigest(),'size':f.stat().st_size})
    duplicates={v:n for v,n in versions.items() if len(n)>1}; latest=max((float(v) for v in versions),default=0)
    doc={'schemaVersion':1,'generatedAt':datetime.now(timezone.utc).isoformat().replace('+00:00','Z'),'migrationCount':len(rows),'latestVersion':str(int(latest)) if latest.is_integer() else str(latest),'invalidFiles':invalid,'duplicates':duplicates,'migrations':rows,'passed':bool(rows) and not invalid and not duplicates}
    out=Path(a.output); out.parent.mkdir(parents=True,exist_ok=True); out.write_text(json.dumps(doc,indent=2,sort_keys=True)+'\n')
    return 0 if doc['passed'] else 2
if __name__=='__main__': raise SystemExit(main())
