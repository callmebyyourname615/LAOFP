#!/usr/bin/env python3
from __future__ import annotations
import json, py_compile, re, subprocess, sys, tempfile
from pathlib import Path
import yaml
ROOT=Path(__file__).resolve().parents[1]; failures=[]
PHASES=[f'61{x}' for x in 'ABCDEFGHIJ']
SCRIPTS=[
 '61A-build-test-green-closure.sh','61B-migration-data-integrity.sh','61C-uat-deployment-contract.sh',
 '61D-smos-security-hardening.sh','61E-dashboard-promotion-readiness.sh','61F-secret-supply-chain-closure.sh',
 '61G-performance-capacity-certification.sh','61H-settlement-reconciliation-scale.sh',
 '61I-resilience-alert-drills.sh','61J-uat-evidence-rc-gate.sh']
REQUIRED=[*(f'scripts/phase61/{x}' for x in SCRIPTS),'scripts/phase61/common.sh','scripts/phase61/run_phase61.sh',
 'scripts/phase61/certify_test_reports.py','scripts/phase61/verify_migration_inventory.py','scripts/phase61/probe_uat_contract.py',
 'scripts/phase61/verify_supply_chain_attestation.py','scripts/phase61/verify_capacity_attestation.py',
 'scripts/phase61/verify_settlement_evidence.py','scripts/phase61/verify_resilience_evidence.py','scripts/phase61/run_resilience_certification.sh',
 'scripts/phase61/build_evidence_manifest.py','scripts/phase61/verify_evidence_manifest.py',
 'scripts/phase61/verify_smos_hardening.py','scripts/phase61/verify_dashboard_promotion_readiness.py',
 'schemas/phase61-result.schema.json','schemas/phase61-evidence-manifest.schema.json',
 'config/phase61-uat-infrastructure-contract.yaml','security/rotation/phase61-supply-chain-inventory.yaml',
 'docs/PHASE_61A_61J_CERTIFICATION.md','.github/workflows/phase61-certification.yml',
 'src/main/resources/db/migration/V101__smos_security_hardening.sql',
 'src/test/java/com/example/switching/migration/V101SmosSecurityHardeningMigrationIntegrationTest.java',
 'src/test/java/com/example/switching/usermgmt/service/SmosSessionSecurityIntegrationTest.java']
for rel in REQUIRED:
 if not (ROOT/rel).is_file(): failures.append(f'missing {rel}')
for rel in [*(f'scripts/phase61/{x}' for x in SCRIPTS),'scripts/phase61/common.sh','scripts/phase61/run_phase61.sh']:
 p=ROOT/rel
 if p.is_file():
  r=subprocess.run(['bash','-n',str(p)],cwd=ROOT,text=True,capture_output=True)
  if r.returncode: failures.append(f'{rel}: bash syntax: {r.stderr.strip()}')
  if not p.stat().st_mode & 0o100: failures.append(f'{rel}: not executable')
with tempfile.TemporaryDirectory() as d:
 for i,p in enumerate(sorted((ROOT/'scripts/phase61').glob('*.py'))):
  try: py_compile.compile(str(p),cfile=str(Path(d)/f'{i}.pyc'),doraise=True)
  except Exception as e: failures.append(f'{p.relative_to(ROOT)}: Python compile: {e}')
for rel in ('schemas/phase61-result.schema.json','schemas/phase61-evidence-manifest.schema.json',
 'docs/templates/PHASE61_SUPPLY_CHAIN_ATTESTATION.example.json','docs/templates/PHASE61_CAPACITY_ATTESTATION.example.json',
 'docs/templates/PHASE61_SETTLEMENT_ATTESTATION.example.json','docs/templates/PHASE61_RESILIENCE_ATTESTATION.example.json',
 'docs/templates/PHASE61_UAT_ENTRY_ATTESTATION.example.json'):
 try: json.loads((ROOT/rel).read_text(encoding='utf-8'))
 except Exception as e: failures.append(f'{rel}: invalid JSON: {e}')
for rel in ('config/phase61-uat-infrastructure-contract.yaml','security/rotation/phase61-supply-chain-inventory.yaml'):
 try: yaml.safe_load((ROOT/rel).read_text(encoding='utf-8'))
 except Exception as e: failures.append(f'{rel}: invalid YAML: {e}')
versions=[]
for p in (ROOT/'src/main/resources/db/migration').glob('V*__*.sql'):
 m=re.match(r'V(\d+)__',p.name)
 if m: versions.append(int(m.group(1)))
if len(versions)!=90 or max(versions,default=0)!=101: failures.append(f'migrations must be 90 through V101, got {len(versions)} through V{max(versions,default=0)}')
if sorted(set(range(1,102))-set(versions))!=[88,89,90,91,92,93,94,95,96,98,99]: failures.append('migration gaps must match the uploaded baseline reservations')
for verifier in ('scripts/phase61/verify_smos_hardening.py','scripts/phase61/verify_dashboard_promotion_readiness.py','scripts/phase61/verify_phase61_evidence_tools.py'):
 r=subprocess.run([sys.executable,str(ROOT/verifier)],cwd=ROOT,text=True,capture_output=True)
 if r.returncode: failures.append(f'{verifier}: {r.stdout.strip()} {r.stderr.strip()}')
workflow=(ROOT/'.github/workflows/phase61-certification.yml').read_text(encoding='utf-8') if (ROOT/'.github/workflows/phase61-certification.yml').is_file() else ''
for marker in ('verify_phase61_static.py','clean verify','run_phase61.sh --repo','upload-artifact'):
 if marker not in workflow: failures.append(f'phase61 workflow missing {marker!r}')
for rel in ('scripts/phase61/61F-secret-supply-chain-closure.sh','scripts/phase61/61G-performance-capacity-certification.sh','scripts/phase61/61H-settlement-reconciliation-scale.sh','scripts/phase61/61I-resilience-alert-drills.sh','scripts/phase61/61J-uat-evidence-rc-gate.sh'):
 if 'phase_require_uat_confirmation' not in (ROOT/rel).read_text(encoding='utf-8'): failures.append(f'{rel}: missing UAT safety guard')
r=subprocess.run(['git','diff','--check'],cwd=ROOT,text=True,capture_output=True)
if r.returncode: failures.append('git diff --check failed: '+r.stdout.strip())
if failures:
 print(f'Phase 61 static contract: FAIL ({len(failures)} issues)')
 for x in failures: print('  ERROR:',x)
 sys.exit(1)
print('Phase 61 static contract: PASS')
