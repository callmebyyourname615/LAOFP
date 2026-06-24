#!/usr/bin/env python3
import argparse, json, sys
from pathlib import Path
from jsonschema import Draft202012Validator

p=argparse.ArgumentParser()
p.add_argument('--phase76-dir',required=True)
p.add_argument('--phase77-dir',required=True)
a=p.parse_args(); root=Path(__file__).resolve().parents[1]
errors=[]
def validate(instance_path,schema_path):
    instance=json.loads(Path(instance_path).read_text()); schema=json.loads((root/schema_path).read_text())
    for e in Draft202012Validator(schema).iter_errors(instance): errors.append(f'{instance_path}:{e.json_path}:{e.message}')
for f in sorted((Path(a.phase76_dir)/'results').glob('*.json')): validate(f,'schemas/phase76/result.schema.json')
validate(Path(a.phase76_dir)/'evidence-ledger.json','schemas/phase76/evidence-ledger.schema.json')
validate(Path(a.phase76_dir)/'release-readiness-manifest.json','schemas/phase76/release-readiness-manifest.schema.json')
for f in sorted((Path(a.phase77_dir)/'results').glob('*.json')): validate(f,'schemas/phase77/result.schema.json')
validate(Path(a.phase77_dir)/'compliance-export.json','schemas/phase77/compliance-export.schema.json')
print(json.dumps({'status':'PASS' if not errors else 'FAIL','errors':errors},indent=2)); sys.exit(1 if errors else 0)
