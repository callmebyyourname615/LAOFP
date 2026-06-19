#!/usr/bin/env python3
import pathlib,re,subprocess,sys,json
try:
 import yaml
except ImportError as exc:
 raise SystemExit('PyYAML required for static verification') from exc
ROOT=pathlib.Path(__file__).resolve().parents[1]
required=[]
for v,name in [
 (63,'double_entry_control_ledger'),(64,'intraday_liquidity_prefunding_controls'),(65,'tariff_fee_governance'),
 (66,'fx_rate_governance'),(67,'participant_certificate_lifecycle'),(68,'regulatory_reporting_submission'),
 (69,'notification_delivery_governance'),(70,'change_freeze_release_calendar'),(71,'synthetic_transaction_monitoring'),
 (72,'incident_corrective_action_management')]:
 required.append(f'src/main/resources/db/migration/V{v}__{name}.sql')
required += [
 'docs/implementation/PHASES_33_42.md','docs/control-evidence/phase33-42-control-map.md',
 'scripts/ledger/verify_double_entry.sh','scripts/liquidity/check_intraday_liquidity.sh',
 'scripts/fees/validate_tariff_bundle.py','scripts/fx/check_fx_governance.sh',
 'scripts/certificates/check_certificate_expiry.sh','scripts/regulatory/build_report_manifest.py','scripts/regulatory/check_report_deadlines.sh',
 'scripts/notifications/lint_templates.py','scripts/release/check_change_window.sh',
 'scripts/synthetic/run_synthetic_probe.py','scripts/incidents/check_overdue_actions.sh',
 'src/main/java/com/example/switching/observability/Phase3342ControlMetrics.java',
 'src/main/java/com/example/switching/fees/TariffGovernanceService.java',
 'src/main/java/com/example/switching/notifications/NotificationTemplateGovernanceService.java',
 'src/main/java/com/example/switching/release/ChangeFreezeExceptionService.java']
missing=[p for p in required if not (ROOT/p).is_file()]
if missing: raise SystemExit('missing required files: '+', '.join(missing))
for p in (ROOT/'src/main/resources/db/migration').glob('V6[3-9]__*.sql'):
 text=p.read_text(encoding='utf-8').lower()
 if 'create table if not exists' not in text: raise SystemExit(f'{p}: guarded table creation missing')
for p in (ROOT/'src/main/resources/db/migration').glob('V7[0-2]__*.sql'):
 text=p.read_text(encoding='utf-8').lower()
 if 'create table if not exists' not in text: raise SystemExit(f'{p}: guarded table creation missing')
for p in (ROOT/'k8s').rglob('*.yaml'):
 list(yaml.safe_load_all(p.read_text(encoding='utf-8')))
for rel in required:
 p=ROOT/rel
 if p.suffix=='.sh': subprocess.run(['bash','-n',str(p)],check=True)
 elif p.suffix=='.py': subprocess.run([sys.executable,'-m','py_compile',str(p)],check=True)
# Security/control invariants
checks={
 'src/main/resources/db/migration/V63__double_entry_control_ledger.sql':['assert_control_journal_balanced','prevent_posted_journal_entry_mutation'],
 'src/main/resources/db/migration/V65__tariff_fee_governance.sql':['approved_by <> requested_by','uq_tariff_active_version'],
 'src/main/resources/db/migration/V67__participant_certificate_lifecycle.sql':['fingerprint_sha256','uq_active_participant_cert'],
 'src/main/resources/db/migration/V71__synthetic_transaction_monitoring.sql':["participant_code LIKE 'SYN%'","synthetic_reference LIKE 'SYN-%'"],
 'src/main/resources/db/migration/V72__incident_corrective_action_management.sql':['incident_closure_approval','corrective_action'],
}
for rel,needles in checks.items():
 text=(ROOT/rel).read_text(encoding='utf-8')
 for needle in needles:
  if needle not in text: raise SystemExit(f'{rel}: missing invariant {needle}')
# Template validator harness
subprocess.run([sys.executable,str(ROOT/'scripts/fees/validate_tariff_bundle.py'),str(ROOT/'docs/templates/tariff_bundle.example.json')],check=True,stdout=subprocess.DEVNULL)
subprocess.run([sys.executable,str(ROOT/'scripts/notifications/lint_templates.py'),str(ROOT/'docs/templates/notification_template.example.json')],check=True,stdout=subprocess.DEVNULL)
import tempfile
with tempfile.TemporaryDirectory() as td:
 d=pathlib.Path(td); (d/'report.csv').write_text('id,amount\n1,10\n',encoding='utf-8')
 subprocess.run([sys.executable,str(ROOT/'scripts/regulatory/build_report_manifest.py'),str(d),str(d/'manifest.json')],check=True,stdout=subprocess.DEVNULL)
 manifest=json.loads((d/'manifest.json').read_text(encoding='utf-8'))
 if len(manifest.get('artifacts',[]))!=1 or manifest['artifacts'][0]['path']!='report.csv': raise SystemExit('regulatory manifest harness failed')

deploy=(ROOT/'.github/workflows/deploy.yml').read_text(encoding='utf-8')
for required_text in ('Enforce production change window and freeze gate','RELEASE_GATE_DB_URL','scripts/release/check_change_window.sh'):
 if required_text not in deploy: raise SystemExit('deploy workflow missing release-calendar gate: '+required_text)
print('Phase 33-42 static acceptance: PASS')
