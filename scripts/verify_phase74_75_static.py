#!/usr/bin/env python3
from __future__ import annotations
import json, subprocess, sys, tempfile
from pathlib import Path
try:
 import yaml
except Exception:
 yaml=None
root=Path(__file__).resolve().parents[1]; errors=[]
required=[
 'scripts/phase74/common.sh','scripts/phase74/run_phase74.sh','scripts/phase75/common.sh','scripts/phase75/run_phase75.sh',
 *[f'scripts/phase74/74{x}-' for x in 'ABCDEFGHIJ'], *[f'scripts/phase75/75{x}-' for x in 'ABCDEFGHIJ'],
 'scripts/phase74/verify_test_reports.py','scripts/phase74/verify_migrations.py','scripts/phase74/verify_attestation.py','scripts/phase74/probe_uat.py','scripts/phase74/verify_financial_evidence.py','scripts/phase74/build_uat_bundle.py',
 'scripts/phase75/verify_phase54_acceptance.py','scripts/phase75/verify_production_attestation.py','scripts/phase75/build_production_handoff.py',
 'config/phase74-uat-runtime-certification.yaml','config/phase74-performance-policy.yaml','config/phase74-resilience-policy.yaml','config/phase75-production-handoff-policy.yaml',
 'schemas/phase74-result.schema.json','schemas/phase75-result.schema.json','schemas/phase74-attestation.schema.json','schemas/phase75-production-decision.schema.json',
 'sql/phase74/post-migration-financial-integrity.sql','.github/workflows/phase74-uat-runtime-certification.yml','.github/workflows/phase75-phase54-production-handoff.yml',
 'scripts/execute-and-verify/14-phase74-uat-runtime-closure.sh','scripts/execute-and-verify/15-phase75-production-handoff.sh',
 'docs/PHASE_74A_75J_IMPLEMENTATION.md','docs/phase74/PHASE74_OVERVIEW.md','docs/phase74/PHASE74_EXIT_CRITERIA.md','docs/phase74/PHASE74_OPERATOR_RUNBOOK.md','docs/phase75/PHASE75_OVERVIEW.md','docs/phase75/PHASE75_EXIT_CRITERIA.md','docs/phase75/PHASE75_OPERATOR_RUNBOOK.md','AGENT/PHASE_74A_75J_CHECKLIST.md'
]
for item in required:
 if item.endswith('-'):
  if not list(root.glob(item+'*.sh')): errors.append('missing pattern '+item+'*.sh')
 elif not (root/item).is_file(): errors.append('missing '+item)
for f in [*sorted((root/'schemas').glob('phase7[45]*.json')),*sorted((root/'docs/templates').glob('PHASE7[45]_*.json'))]:
 try: json.loads(f.read_text())
 except Exception as e: errors.append(f'{f.relative_to(root)} invalid JSON: {e}')
for f in [root/'config/phase74-uat-runtime-certification.yaml',root/'config/phase74-performance-policy.yaml',root/'config/phase74-resilience-policy.yaml',root/'config/phase75-production-handoff-policy.yaml',root/'.github/workflows/phase74-uat-runtime-certification.yml',root/'.github/workflows/phase75-phase54-production-handoff.yml']:
 try:
  if yaml: yaml.safe_load(f.read_text())
 except Exception as e: errors.append(f'{f.relative_to(root)} invalid YAML: {e}')
for f in [*sorted((root/'scripts/phase74').glob('*.sh')),*sorted((root/'scripts/phase75').glob('*.sh')),root/'scripts/execute-and-verify/14-phase74-uat-runtime-closure.sh',root/'scripts/execute-and-verify/15-phase75-production-handoff.sh']:
 cp=subprocess.run(['bash','-n',str(f)],capture_output=True,text=True)
 if cp.returncode: errors.append(f'{f.relative_to(root)} bash syntax: {cp.stderr.strip()}')
for f in [root/'scripts/verify_phase74_75_static.py',*sorted((root/'scripts/phase74').glob('*.py')),*sorted((root/'scripts/phase75').glob('*.py'))]:
 cp=subprocess.run([sys.executable,'-m','py_compile',str(f)],capture_output=True,text=True)
 if cp.returncode: errors.append(f'{f.relative_to(root)} python syntax: {cp.stderr.strip()}')
run_all=(root/'scripts/execute-and-verify/00-run-all.sh').read_text(errors='replace')
for token in ('14-phase74-uat-runtime-closure','15-phase75-production-handoff'):
 if token not in run_all: errors.append('00-run-all.sh missing '+token)
# Example attestations must fail closed because they contain placeholders.
example=root/'docs/templates/PHASE74_UAT_INFRA_ATTESTATION.example.json'
with tempfile.TemporaryDirectory() as td:
 cp=subprocess.run([sys.executable,str(root/'scripts/phase74/verify_attestation.py'),'--kind','uat-infra','--file',str(example),'--output',str(Path(td)/'out.json')],capture_output=True,text=True)
 if cp.returncode==0: errors.append('placeholder Phase 74 attestation was accepted')
example2=root/'docs/templates/PHASE75_PRODUCTION_DECISION_ATTESTATION.example.json'
with tempfile.TemporaryDirectory() as td:
 cp=subprocess.run([sys.executable,str(root/'scripts/phase75/verify_production_attestation.py'),'--kind','production-decision','--file',str(example2),'--output',str(Path(td)/'out.json'),'--commit','a'*40,'--application-digest','sha256:'+'b'*64,'--migration-digest','sha256:'+'c'*64],capture_output=True,text=True)
 if cp.returncode==0: errors.append('placeholder Phase 75 attestation was accepted')
print('Phase 74/75 static contract PASS' if not errors else '\n'.join('FAIL: '+e for e in errors))
sys.exit(0 if not errors else 1)
