#!/usr/bin/env python3
import json, os, pathlib, re, subprocess, sys, yaml
ROOT = pathlib.Path(__file__).resolve().parents[1]
required = [
  'src/main/resources/db/migration/V53__reconciliation_automation.sql',
  'src/main/resources/db/migration/V54__fraud_velocity_controls.sql',
  'src/main/resources/db/migration/V55__participant_lifecycle_sla.sql',
  'src/main/resources/db/migration/V56__iso_message_validation_packs.sql',
  'src/main/resources/db/migration/V57__settlement_evidence_ledger.sql',
  'src/main/resources/db/migration/V58__operations_command_center.sql',
  'src/main/resources/db/migration/V59__data_quality_controls.sql',
  'src/main/resources/db/migration/V60__multi_region_readiness.sql',
  'src/main/resources/db/migration/V61__privacy_case_management.sql',
  'src/main/resources/db/migration/V62__continuous_compliance_controls.sql',
  'scripts/reconciliation/run_daily_reconciliation.sh',
  'scripts/compliance/run_control_pack.sh',
  'docs/implementation/PHASES_23_32.md',
]
missing=[p for p in required if not (ROOT/p).exists()]
if missing:
    raise SystemExit('missing required files: ' + ', '.join(missing))
for path in (ROOT/'k8s').rglob('*.yaml'):
    list(yaml.safe_load_all(path.read_text(encoding='utf-8')))
phase_scripts = [
  'scripts/reconciliation/run_daily_reconciliation.sh',
  'scripts/reconciliation/compare_reconciliation_counts.py',
  'scripts/fraud/load_velocity_rules.sh',
  'scripts/fraud/validate_velocity_rules.py',
  'scripts/participant/lifecycle_gate.sh',
  'scripts/iso/validate_pack_manifest.py',
  'scripts/settlement/build_evidence_bundle.sh',
  'scripts/ops/open_daily_control_room.sh',
  'scripts/dataquality/run_data_quality.sh',
  'scripts/resilience/region_readiness_probe.sh',
  'scripts/privacy/export_subject_case.sh',
  'scripts/compliance/run_control_pack.sh',
]
for rel in phase_scripts:
    path = ROOT / rel
    if path.suffix == '.sh':
        subprocess.run(['bash','-n',str(path)], check=True)
    elif path.suffix == '.py':
        subprocess.run([sys.executable,'-m','py_compile',str(path)], check=True)
for path in (ROOT/'src/main/resources/db/migration').glob('V5[3-9]__*.sql'):
    text=path.read_text(encoding='utf-8').lower()
    if 'create table if not exists' not in text:
        raise SystemExit(f'migration does not create guarded tables: {path}')
print('Phase 23-32 static acceptance: PASS')
