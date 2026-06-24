#!/usr/bin/env python3
from __future__ import annotations
import json,subprocess,sys,tempfile
from pathlib import Path
try: import yaml
except Exception: yaml=None
root=Path(__file__).resolve().parents[1]; errors=[]
required=['scripts/phase78/common.sh','scripts/phase78/run_phase78.sh','scripts/phase79/common.sh','scripts/phase79/run_phase79.sh','scripts/phase78/verify_source_convergence.py','scripts/phase78/verify_attestation.py','scripts/phase78/build_uat_closure.py','scripts/phase79/verify_production_attestation.py','scripts/phase79/build_production_bundle.py','config/phase78-source-convergence.yaml','config/phase78-uat-execution-policy.yaml','config/phase78-performance-policy.yaml','config/phase78-resilience-policy.yaml','config/phase79-production-cutover-policy.yaml','schemas/phase78-result.schema.json','schemas/phase79-result.schema.json','schemas/phase78-attestation.schema.json','schemas/phase79-attestation.schema.json','sql/phase78/post-migration-financial-integrity.sql','.github/workflows/phase78-final-uat-execution.yml','.github/workflows/phase79-production-golive.yml','scripts/execute-and-verify/16-phase78-final-uat-execution.sh','scripts/execute-and-verify/17-phase79-production-golive.sh','docs/PHASE_78A_79J_IMPLEMENTATION.md','AGENT/PHASE_78A_79J_CHECKLIST.md']
required += [f'scripts/phase78/78{x}-' for x in 'ABCDEFGHIJ']+[f'scripts/phase79/79{x}-' for x in 'ABCDEFGHIJ']
for item in required:
 if item.endswith('-'):
  if not list(root.glob(item+'*.sh')): errors.append('missing pattern '+item+'*.sh')
 elif not (root/item).is_file(): errors.append('missing '+item)
for f in [*root.glob('schemas/phase7[89]*.json'),*root.glob('docs/templates/PHASE7[89]_*.json')]:
 try: json.loads(f.read_text())
 except Exception as e: errors.append(f'{f.relative_to(root)} invalid JSON: {e}')
for f in [*root.glob('config/phase7[89]-*.yaml'),root/'.github/workflows/phase78-final-uat-execution.yml',root/'.github/workflows/phase79-production-golive.yml']:
 try:
  if yaml: yaml.safe_load(f.read_text())
 except Exception as e: errors.append(f'{f.relative_to(root)} invalid YAML: {e}')
for f in [*root.glob('scripts/phase78/*.sh'),*root.glob('scripts/phase79/*.sh'),root/'scripts/execute-and-verify/16-phase78-final-uat-execution.sh',root/'scripts/execute-and-verify/17-phase79-production-golive.sh']:
 cp=subprocess.run(['bash','-n',str(f)],capture_output=True,text=True)
 if cp.returncode: errors.append(f'{f.relative_to(root)} bash syntax: {cp.stderr.strip()}')
for f in [root/'scripts/verify_phase78_79_static.py',*root.glob('scripts/phase78/*.py'),*root.glob('scripts/phase79/*.py')]:
 cp=subprocess.run([sys.executable,'-m','py_compile',str(f)],capture_output=True,text=True)
 if cp.returncode: errors.append(f'{f.relative_to(root)} python syntax: {cp.stderr.strip()}')
run_all=(root/'scripts/execute-and-verify/00-run-all.sh').read_text(errors='replace')
for token in ('16-phase78-final-uat-execution','17-phase79-production-golive'):
 if token not in run_all: errors.append('00-run-all missing '+token)
with tempfile.TemporaryDirectory() as td:
 cp=subprocess.run([sys.executable,str(root/'scripts/phase78/verify_attestation.py'),'--kind','uat-infrastructure','--file',str(root/'docs/templates/PHASE78_UAT_INFRA_ATTESTATION.example.json'),'--output',str(Path(td)/'x.json')],capture_output=True,text=True)
 if cp.returncode==0: errors.append('Phase 78 placeholder accepted')
 cp=subprocess.run([sys.executable,str(root/'scripts/phase79/verify_production_attestation.py'),'--kind','production-go','--file',str(root/'docs/templates/PHASE79_PRODUCTION_GO_ATTESTATION.example.json'),'--output',str(Path(td)/'y.json'),'--commit','a'*40,'--application-digest','sha256:'+'b'*64,'--migration-digest','sha256:'+'c'*64],capture_output=True,text=True)
 if cp.returncode==0: errors.append('Phase 79 placeholder accepted')
print('Phase 78/79 static contract PASS' if not errors else '\n'.join('FAIL: '+e for e in errors)); raise SystemExit(0 if not errors else 1)
