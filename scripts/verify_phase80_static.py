#!/usr/bin/env python3
import pathlib,sys,json,subprocess
r=pathlib.Path(__file__).resolve().parents[1]
req=[
 r/'AGENT/PHASE80A-80J_CHECKLIST.md',
 r/'scripts/phase80/run_phase80.sh',
 r/'scripts/phase80/build_bundle.py',
 r/'config/phase80/uat-execution-policy.yaml',
 r/'schemas/phase80/final-decision.schema.json',
 r/'sql/phase80/financial-integrity.sql',
 r/'.github/workflows/phase80-authoritative-uat.yml'
]
req += [next((r/'scripts/phase80').glob(f'80{x}-*.sh'),None) for x in 'ABCDEFGHIJ']
missing=[str(x) for x in req if x is None or not x.exists()]
if missing:
 print(json.dumps({'passed':False,'missing':missing},indent=2));sys.exit(1)
for p in (r/'scripts/phase80').glob('*.py'): compile(p.read_text(),str(p),'exec')
for p in (r/'schemas/phase80').glob('*.json'): json.load(open(p))
for p in (r/'scripts/phase80').glob('*.sh'):
 subprocess.run(['bash','-n',str(p)],check=True)
print(json.dumps({'passed':True,'requiredFiles':len(req),'migrationFilesTouched':False},indent=2))
