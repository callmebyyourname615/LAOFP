#!/usr/bin/env python3
import argparse, hashlib, json
from datetime import datetime, timezone
from pathlib import Path
p=argparse.ArgumentParser(); p.add_argument('--source',required=True); p.add_argument('--output',required=True); p.add_argument('--release',required=True)
a=p.parse_args(); root=Path(a.source); output=Path(a.output).resolve()
arts=[{'path':str(f.relative_to(root)),'sha256':hashlib.sha256(f.read_bytes()).hexdigest()} for f in sorted(root.rglob('*')) if f.is_file() and f.resolve()!=output]
out={'release':a.release,'generated_at':datetime.now(timezone.utc).isoformat(),'artifacts':arts,'contains_secret_values':False,'signed':False,'runtime_certified':False}
Path(a.output).parent.mkdir(parents=True,exist_ok=True); Path(a.output).write_text(json.dumps(out,indent=2)+'\n')
