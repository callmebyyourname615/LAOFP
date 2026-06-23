#!/usr/bin/env python3
import json, subprocess, sys
from pathlib import Path
try:
 import yaml
except Exception:
 yaml=None
root=Path(__file__).resolve().parents[1]; errors=[]
required=[
 'scripts/phase68/common.sh','scripts/phase68/run_phase68.sh',
 *[f'scripts/phase68/68{x}-' for x in 'ABCDEFGHIJ'],
 'scripts/phase68/verify_p0_test_fixes.py','scripts/phase68/verify_migration_inventory.py',
 'scripts/phase68/audit_admin_authorization.py','scripts/phase68/verify_attestation.py',
 'scripts/phase68/build_phase54_kickoff_bundle.py','scripts/phase68/probe_uat_runtime.py','scripts/phase68/generate_rotated_secrets.sh',
 'config/phase68-uat-activation.yaml','config/phase68-performance-policy.yaml','config/phase68-resilience-policy.yaml',
 'schemas/phase68-result.schema.json','.github/workflows/phase68-certification.yml',
 'scripts/execute-and-verify/10-phase68-preflight.sh','docs/PHASE_68A_68J_IMPLEMENTATION.md'
]
for item in required:
 if item.endswith('-'):
  if not list(root.glob(item+'*.sh')): errors.append('missing pattern '+item+'*.sh')
 elif not (root/item).is_file(): errors.append('missing '+item)
# Parse JSON/YAML
for f in [root/'schemas/phase68-result.schema.json',*sorted((root/'docs/templates').glob('PHASE68_*.json'))]:
 try: json.loads(f.read_text())
 except Exception as e: errors.append(f'{f.relative_to(root)} invalid JSON: {e}')
for f in [root/'config/phase68-uat-activation.yaml',root/'config/phase68-performance-policy.yaml',root/'config/phase68-resilience-policy.yaml',root/'.github/workflows/phase68-certification.yml']:
 try:
  if yaml: yaml.safe_load(f.read_text())
 except Exception as e: errors.append(f'{f.relative_to(root)} invalid YAML: {e}')
# Bash syntax
for f in sorted((root/'scripts/phase68').glob('*.sh'))+[root/'scripts/execute-and-verify/10-phase68-preflight.sh']:
 cp=subprocess.run(['bash','-n',str(f)],capture_output=True,text=True)
 if cp.returncode: errors.append(f'{f.relative_to(root)} bash syntax: {cp.stderr.strip()}')
# Python syntax
for f in [root/'scripts/verify_phase68_static.py',*sorted((root/'scripts/phase68').glob('*.py'))]:
 cp=subprocess.run([sys.executable,'-m','py_compile',str(f)],capture_output=True,text=True)
 if cp.returncode: errors.append(f'{f.relative_to(root)} python syntax: {cp.stderr.strip()}')
# Security annotations must be explicit
for rel,token in {
 'src/main/java/com/example/switching/settlement/controller/SettlementController.java':'PERM_SETTLEMENT_APPROVE',
 'src/main/java/com/example/switching/participant/controller/ParticipantController.java':'PERM_PARTICIPANT_MANAGE',
 'src/main/java/com/example/switching/participant/controller/ParticipantCredentialController.java':'PERM_PARTICIPANT_MANAGE'
}.items():
 text=(root/rel).read_text(errors='replace')
 if '@PreAuthorize' not in text or token not in text: errors.append(f'{rel} missing {token} @PreAuthorize')
print('Phase 68 static contract PASS' if not errors else '\n'.join('FAIL: '+e for e in errors))
sys.exit(0 if not errors else 1)
