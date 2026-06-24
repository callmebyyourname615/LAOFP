#!/usr/bin/env python3
import argparse, hashlib, json
from datetime import datetime, timezone
from pathlib import Path
p=argparse.ArgumentParser(); p.add_argument('--input-dir',required=True); p.add_argument('--output',required=True); p.add_argument('--git-commit',required=True)
a=p.parse_args(); files=sorted(x for x in Path(a.input_dir).rglob('*') if x.is_file() and x.resolve()!=Path(a.output).resolve())
prev='0'*64; entries=[]
for i,f in enumerate(files,1):
    digest=hashlib.sha256(f.read_bytes()).hexdigest(); rec={'sequence':i,'path':str(f.relative_to(a.input_dir)),'sha256':digest,'git_commit':a.git_commit,'previous_hash':prev}
    rec['record_hash']=hashlib.sha256(json.dumps(rec,sort_keys=True,separators=(',',':')).encode()).hexdigest(); prev=rec['record_hash']; entries.append(rec)
out={'schema_version':'1.0','generated_at':datetime.now(timezone.utc).isoformat(),'entry_count':len(entries),'tail_hash':prev,'entries':entries}
Path(a.output).parent.mkdir(parents=True,exist_ok=True); Path(a.output).write_text(json.dumps(out,indent=2)+'\n')
