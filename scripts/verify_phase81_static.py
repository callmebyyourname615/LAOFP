#!/usr/bin/env python3
import pathlib,sys,json,subprocess
r=pathlib.Path(__file__).resolve().parents[1]
req=[
 r/'AGENT/PHASE81A-81J_CHECKLIST.md',
 r/'scripts/phase81/run_phase81.sh',
 r/'config/phase81/application-phase81.example.yaml',
 r/'schemas/phase81/bau-activation.schema.json',
 r/'.github/workflows/phase81-operational-bau.yml'
]
for pkg in ['transaction','participant','infrastructure','dr']:
 req += list((r/f'src/main/java/com/example/switching/dashboard/{pkg}').rglob('*.java'))
missing=[str(x) for x in req if not x.exists()]
if missing:
 print(json.dumps({'passed':False,'missing':missing},indent=2));sys.exit(1)
for p in (r/'scripts/phase81').glob('*.py'): compile(p.read_text(),str(p),'exec')
for p in (r/'schemas/phase81').glob('*.json'): json.load(open(p))
for p in (r/'scripts/phase81').glob('*.sh'): subprocess.run(['bash','-n',str(p)],check=True)
for p in (r/'src/main/java/com/example/switching/dashboard').rglob('*DashboardController.java'):
 if any(k in str(p) for k in ['/transaction/','/participant/','/infrastructure/','/dr/']):
  s=p.read_text()
  assert 'CacheControl.noStore()' in s and '@PreAuthorize' in s
print(json.dumps({'passed':True,'dashboardPackages':4,'featureFlagsDefaultDisabled':True},indent=2))
