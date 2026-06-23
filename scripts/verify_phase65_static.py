#!/usr/bin/env python3
from pathlib import Path
import json, os, re, subprocess, sys
try:
 import yaml
except Exception:
 yaml=None
errors=[]
required=[
 'AGENT/PHASE_65A_65J_CHECKLIST.md','scripts/phase65/common.sh','scripts/phase65/run_phase65.sh',
 'scripts/phase65/verify_historical_test_blockers.py','scripts/phase65/certify_test_reports.py','scripts/phase65/verify_migration_chain.py',
 'scripts/phase65/provision_smos_operators.py','scripts/phase65/verify_smos_runtime_readiness.py',
 'scripts/phase65/verify_secret_rotation_attestation.py','security/scripts/generate_phase65_rotated_secrets.sh',
 'scripts/phase65/probe_uat_environment.py','scripts/phase65/verify_nested_results.py','scripts/phase65/build_phase54_handoff.py',
 'config/phase65-uat-infrastructure-contract.yaml','docs/PHASE_65A_65J_IMPLEMENTATION.md',
 '.github/workflows/phase65-certification.yml','scripts/execute-and-verify/09-phase65-preflight.sh']
required += [f'scripts/phase65/65{x}-' for x in 'ABCDEFGHIJ']
for item in required:
 if item.endswith('-'):
  if not list(Path('scripts/phase65').glob(Path(item).name+'*.sh')): errors.append('missing phase script '+item+'*.sh')
 elif not Path(item).is_file(): errors.append('missing '+item)
phase_scripts=[]
for x in 'ABCDEFGHIJ':
 matches=list(Path('scripts/phase65').glob(f'65{x}-*.sh'))
 if len(matches)!=1: errors.append(f'expected exactly one 65{x} script, found {len(matches)}')
 phase_scripts.extend(matches)
if len(phase_scripts)!=10: errors.append(f'expected 10 Phase 65 scripts, found {len(phase_scripts)}')
for p in Path('scripts/phase65').glob('*.sh'):
 r=subprocess.run(['bash','-n',str(p)],capture_output=True,text=True)
 if r.returncode: errors.append(f'{p}: bash syntax: {r.stderr.strip()}')
for p in list(Path('scripts/phase65').glob('*.py'))+[Path('scripts/verify_phase65_static.py')]:
 r=subprocess.run([sys.executable,'-m','py_compile',str(p)],capture_output=True,text=True)
 if r.returncode: errors.append(f'{p}: Python syntax: {r.stderr.strip()}')
for p in Path('docs/templates').glob('PHASE65_*.json'):
 try: json.loads(p.read_text())
 except Exception as e: errors.append(f'{p}: JSON invalid: {e}')
if yaml:
 try: yaml.safe_load(Path('config/phase65-uat-infrastructure-contract.yaml').read_text())
 except Exception as e: errors.append('phase65 UAT YAML invalid: '+str(e))
secretgen=Path('security/scripts/generate_phase65_rotated_secrets.sh').read_text()
if 'Refusing to write generated secrets inside repository' not in secretgen or 'umask 077' not in secretgen: errors.append('secret generator lacks repository-path guard or restrictive umask')
phase65i=next(iter(Path('scripts/phase65').glob('65I-*.sh')),None)
if not phase65i or 'Phase 64 source package is not present' not in phase65i.read_text(): errors.append('65I must block honestly when authoritative Phase 64 is absent')
reg=subprocess.run([sys.executable,'scripts/phase65/verify_historical_test_blockers.py'],capture_output=True,text=True)
if reg.returncode: errors.append('historical blocker verifier failed: '+reg.stdout+reg.stderr)
if errors:
 print('\n'.join('FAIL: '+x for x in errors)); sys.exit(1)
print('PASS: Phase 65A–65J static contract, safety guards and source regressions')
