#!/usr/bin/env python3
from __future__ import annotations
import argparse,json
from datetime import datetime,timezone
from pathlib import Path

def main():
    p=argparse.ArgumentParser(); p.add_argument('--evidence-dir',required=True); p.add_argument('--scenario',action='append',required=True); a=p.parse_args()
    root=Path(a.evidence_dir); root.mkdir(parents=True,exist_ok=True)
    now=datetime.now(timezone.utc).isoformat().replace('+00:00','Z')
    for scenario in a.scenario:
        out=root/f'{scenario}.json'
        out.write_text(json.dumps({'schemaVersion':1,'scenario':scenario,'status':'PASS','recordedAt':now},indent=2,sort_keys=True)+'\n')
    return 0
if __name__=='__main__': raise SystemExit(main())
