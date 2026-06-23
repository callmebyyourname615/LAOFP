#!/usr/bin/env python3
import json, subprocess, sys
from pathlib import Path
try:
 import yaml
except Exception:
 yaml=None
root=Path(__file__).resolve().parents[1]; errors=[]
required=[
 'scripts/phase71/common.sh','scripts/phase71/run_phase71.sh',
 *[f'scripts/phase71/71{x}-' for x in 'ABCDEFGHIJ'],
 'scripts/phase71/verify_timestamp_binding.py','scripts/phase71/verify_test_reports.py',
 'scripts/phase71/verify_migrations.py','scripts/phase71/verify_attestation.py',
 'scripts/phase71/probe_uat.py','scripts/phase71/build_uat_bundle.py',
 'src/main/java/com/example/switching/jdbc/JdbcTemporalBinder.java',
 'src/test/java/com/example/switching/jdbc/JdbcTemporalBinderTest.java',
 'sql/phase71/data-integrity-checks.sql',
 'config/phase71-uat-certification.yaml','config/phase71-performance-policy.yaml','config/phase71-resilience-policy.yaml',
 'schemas/phase71-result.schema.json','schemas/phase71-attestation.schema.json',
 '.github/workflows/phase71-uat-certification.yml','scripts/execute-and-verify/11-phase71-preflight.sh',
 'docs/PHASE_71A_71J_IMPLEMENTATION.md','docs/phase71/PHASE71_OVERVIEW.md',
 'docs/phase71/PHASE71_EXIT_CRITERIA.md','docs/phase71/PHASE71_OPERATOR_RUNBOOK.md'
]
for item in required:
 if item.endswith('-'):
  if not list(root.glob(item+'*.sh')): errors.append('missing pattern '+item+'*.sh')
 elif not (root/item).is_file(): errors.append('missing '+item)
for f in [root/'schemas/phase71-result.schema.json',root/'schemas/phase71-attestation.schema.json',*sorted((root/'docs/templates').glob('PHASE71_*.json'))]:
 try: json.loads(f.read_text())
 except Exception as e: errors.append(f'{f.relative_to(root)} invalid JSON: {e}')
for f in [root/'config/phase71-uat-certification.yaml',root/'config/phase71-performance-policy.yaml',root/'config/phase71-resilience-policy.yaml',root/'.github/workflows/phase71-uat-certification.yml']:
 try:
  if yaml: yaml.safe_load(f.read_text())
 except Exception as e: errors.append(f'{f.relative_to(root)} invalid YAML: {e}')
for f in sorted((root/'scripts/phase71').glob('*.sh'))+[root/'scripts/execute-and-verify/11-phase71-preflight.sh']:
 cp=subprocess.run(['bash','-n',str(f)],capture_output=True,text=True)
 if cp.returncode: errors.append(f'{f.relative_to(root)} bash syntax: {cp.stderr.strip()}')
for f in [root/'scripts/verify_phase71_static.py',*sorted((root/'scripts/phase71').glob('*.py'))]:
 cp=subprocess.run([sys.executable,'-m','py_compile',str(f)],capture_output=True,text=True)
 if cp.returncode: errors.append(f'{f.relative_to(root)} python syntax: {cp.stderr.strip()}')
service=(root/'src/main/java/com/example/switching/crossborder/service/CrossBorderTransferService.java').read_text(errors='replace')
for token in ('JdbcTemporalBinder.bindTimestamp','JdbcTemporalBinder.bindDate'):
 if token not in service: errors.append(f'CrossBorderTransferService missing {token}')
run_all=(root/'scripts/execute-and-verify/00-run-all.sh').read_text(errors='replace')
if '11-phase71-preflight' not in run_all: errors.append('00-run-all.sh missing Phase 71 preflight')
print('Phase 71 static contract PASS' if not errors else '\n'.join('FAIL: '+e for e in errors))
sys.exit(0 if not errors else 1)
