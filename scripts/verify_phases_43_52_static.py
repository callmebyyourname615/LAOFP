#!/usr/bin/env python3
import contextlib,hashlib,importlib.util,io,json,pathlib,re,subprocess,sys,tempfile
try:
    import yaml
except ImportError as exc:
    raise SystemExit('PyYAML required for static verification') from exc
ROOT=pathlib.Path(__file__).resolve().parents[1]
MIGRATIONS={
73:'transaction_limit_entitlement_governance',74:'manual_financial_adjustment_governance',75:'settlement_calendar_cutoff_governance',
76:'payment_finality_duplicate_protection',77:'cryptographic_asset_inventory_rotation',78:'third_party_dependency_sla_governance',
79:'capacity_forecast_autoscaling_governance',80:'data_lineage_evidence_catalog',81:'decision_rule_model_governance',
82:'controlled_decommissioning_data_exit'}
required=[f'src/main/resources/db/migration/V{v}__{name}.sql' for v,name in MIGRATIONS.items()]
required += [
 'PHASES_43_52_DELIVERY_NOTES.md','docs/implementation/PHASES_43_52.md','docs/control-evidence/phase43-52-control-map.md',
 '.github/workflows/phase43-52-control-gates.yml','k8s/configmaps/phase43-52-control-policy.yaml',
 'k8s/cronjobs/phase43-52-control-cronjobs.yaml','monitoring/prometheus/phase43-52-alert-rules.yaml',
 'src/main/java/com/example/switching/governance/ControlEvidence.java',
 'src/main/java/com/example/switching/limits/TransactionLimitGovernanceService.java',
 'src/main/java/com/example/switching/adjustments/ManualFinancialAdjustmentService.java',
 'src/main/java/com/example/switching/settlement/calendar/SettlementCalendarGovernanceService.java',
 'src/main/java/com/example/switching/finality/PaymentFinalityService.java',
 'src/main/java/com/example/switching/crypto/CryptographicAssetGovernanceService.java',
 'src/main/java/com/example/switching/dependency/ThirdPartyDependencyControlService.java',
 'src/main/java/com/example/switching/capacity/CapacityGovernanceService.java',
 'src/main/java/com/example/switching/lineage/DataLineageEvidenceService.java',
 'src/main/java/com/example/switching/rules/DecisionRuleGovernanceService.java',
 'src/main/java/com/example/switching/decommission/ControlledDecommissionService.java',
 'src/main/java/com/example/switching/observability/Phase4352ControlMetrics.java',
 'scripts/limits/validate_limit_policy.py','scripts/adjustments/check_pending_adjustments.sh',
 'scripts/settlement/validate_calendar.py','scripts/finality/check_finality_integrity.sh',
 'scripts/crypto/check_crypto_inventory.sh','scripts/dependencies/probe_dependencies.py','scripts/dependencies/validate_dependency_config.py',
 'scripts/capacity/build_capacity_forecast.py','scripts/capacity/check_autoscaling_guardrails.py',
 'scripts/lineage/validate_lineage.py','scripts/lineage/verify_evidence.py','scripts/lineage/check_unverified_evidence.sh',
 'scripts/rules/validate_rule_package.py','scripts/rules/evaluate_golden_cases.py','scripts/decommission/check_readiness.sh','scripts/decommission/validate_plan.py',
 'docs/templates/hpa.phase43-52.example.yaml','docs/templates/decision_rule_golden_cases.example.json','docs/templates/decision_rule_golden_results.example.json',
 'monitoring/grafana/dashboards/switching-phase43-52-controls.json']
missing=[p for p in required if not (ROOT/p).is_file()]
if missing: raise SystemExit('missing required files: '+', '.join(missing))

# Syntax/config parsing
for p in list((ROOT/'k8s').rglob('*.yaml'))+list((ROOT/'monitoring').rglob('*.yaml'))+list((ROOT/'.github/workflows').glob('*.yml')):
    list(yaml.safe_load_all(p.read_text(encoding='utf-8')))
for p in list((ROOT/'docs/templates').glob('*.json'))+list((ROOT/'monitoring/grafana/dashboards').glob('*.json')): json.loads(p.read_text(encoding='utf-8'))
for rel in required:
    p=ROOT/rel
    if p.suffix=='.sh': subprocess.run(['bash','-n',str(p)],check=True)
    elif p.suffix=='.py': compile(p.read_text(encoding='utf-8'),str(p),'exec')

# Migration safety and invariants
for v,name in MIGRATIONS.items():
    p=ROOT/f'src/main/resources/db/migration/V{v}__{name}.sql';text=p.read_text(encoding='utf-8')
    if 'CREATE TABLE IF NOT EXISTS' not in text: raise SystemExit(f'{p}: guarded table creation missing')
checks={
 73:['transaction_limit_consumption','approved_by IS NULL OR approved_by <> requested_by','transaction_limit_decision_audit'],
 74:['assert_manual_adjustment_balanced','prevent_executed_adjustment_mutation','execution_journal_id'],
 75:['uq_settlement_calendar_active','settlement_cutoff_decision','finality_cutoff >= submission_cutoff'],
 76:['payment_idempotency_record','payment_duplicate_fingerprint','prevent_final_payment_mutation','risk_approved_by'],
 77:['external_reference !~*','cryptographic_rotation_plan','artifact_sha256'],
 78:['third_party_circuit_state','FORCED_OPEN','approved_by IS NULL OR approved_by <> requested_by'],
 79:['uq_governed_autoscaling_active','performance_approved_by','rollback_reference'],
 80:['prevent_sealed_evidence_mutation','prevent_data_lineage_cycle','data_lineage_edge','control_evidence_verification'],
 81:['uq_decision_rule_active_version','decision_rule_test_execution','compliance_approved_by'],
 82:['operations_approved_by','risk_approved_by','business_approved_by','decommission_data_exit_artifact']}
for v,needles in checks.items():
    text=(ROOT/f'src/main/resources/db/migration/V{v}__{MIGRATIONS[v]}.sql').read_text(encoding='utf-8')
    for needle in needles:
        if needle not in text: raise SystemExit(f'V{v}: missing invariant {needle}')

# Runtime isolation and security invariants
metrics=(ROOT/'src/main/java/com/example/switching/observability/Phase4352ControlMetrics.java').read_text(encoding='utf-8')
if '@Profile("!migration")' not in metrics: raise SystemExit('Phase4352 metrics must be disabled for migration profile')
crypto=(ROOT/'src/main/java/com/example/switching/crypto/CryptographicAssetGovernanceService.java').read_text(encoding='utf-8')
if 'secret material must never be stored' not in crypto: raise SystemExit('crypto inventory secret-material guard missing')
finality=(ROOT/'src/main/java/com/example/switching/finality/PaymentFinalityService.java').read_text(encoding='utf-8')
if 'idempotency key reused with different request payload' not in finality: raise SystemExit('idempotency payload binding missing')
for needle in ('duplicateFingerprint(', 'sourceAccountHash', 'destinationAccountHash', 'amount.stripTrailingZeros().toPlainString()'):
    if needle not in finality: raise SystemExit('server-side canonical duplicate fingerprint missing: '+needle)
limits_runtime=(ROOT/'src/main/java/com/example/switching/limits/TransactionLimitGovernanceService.java').read_text(encoding='utf-8')
for needle in ('List<PendingConsumption> pendingConsumptions','pendingConsumptions.forEach(this::applyConsumption)','limit-tx|'):
    if needle not in limits_runtime: raise SystemExit('atomic/idempotent transaction limit invariant missing: '+needle)
capacity_runtime=(ROOT/'src/main/java/com/example/switching/capacity/CapacityGovernanceService.java').read_text(encoding='utf-8')
for needle in ('requestPolicyActivation','applyApprovedPolicy','capacity request is not fully approved'):
    if needle not in capacity_runtime: raise SystemExit('governed autoscaling lifecycle missing: '+needle)
decommission_runtime=(ROOT/'src/main/java/com/example/switching/decommission/ControlledDecommissionService.java').read_text(encoding='utf-8')
for needle in ('blocking decommission tasks cannot be waived','cannot execute before planned effective time',"status <> 'DONE'"):
    if needle not in decommission_runtime: raise SystemExit('decommission fail-closed invariant missing: '+needle)
migration_test=(ROOT/'src/test/java/com/example/switching/migration/MigrationApplicationIntegrationTest.java').read_text(encoding='utf-8')
if 'isEqualTo("96")' not in migration_test: raise SystemExit('migration integration test must assert latest version 83')
workflow=(ROOT/'.github/workflows/phase43-52-control-gates.yml').read_text(encoding='utf-8')
for needle in ('clean verify','verify_phases_43_52_static.py','phase43-52-control-evidence'):
    if needle not in workflow: raise SystemExit('workflow missing '+needle)
cron=(ROOT/'k8s/cronjobs/phase43-52-control-cronjobs.yaml').read_text(encoding='utf-8')
for needle in ('@sha256:REPLACE_WITH_DIGEST','runAsNonRoot: true','readOnlyRootFilesystem: true','suspend: true'):
    if needle not in cron: raise SystemExit('CronJob hardening/default-suspend invariant missing: '+needle)

# Positive validator harnesses (in-process to keep CI fast and deterministic)
def load_module(name, relative):
    path=ROOT/relative
    spec=importlib.util.spec_from_file_location(name,path)
    module=importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
    return module
limits=load_module('phase4352_limits','scripts/limits/validate_limit_policy.py')
calendar=load_module('phase4352_calendar','scripts/settlement/validate_calendar.py')
lineage=load_module('phase4352_lineage','scripts/lineage/validate_lineage.py')
rules=load_module('phase4352_rules','scripts/rules/validate_rule_package.py')
deps=load_module('phase4352_deps','scripts/dependencies/validate_dependency_config.py')
decommission=load_module('phase4352_decommission','scripts/decommission/validate_plan.py')
capacity=load_module('phase4352_capacity','scripts/capacity/build_capacity_forecast.py')
evidence=load_module('phase4352_evidence','scripts/lineage/verify_evidence.py')
autoscaling=load_module('phase4352_autoscaling','scripts/capacity/check_autoscaling_guardrails.py')
golden=load_module('phase4352_golden','scripts/rules/evaluate_golden_cases.py')
with contextlib.redirect_stdout(io.StringIO()):
    limits.main(ROOT/'docs/templates/transaction_limits.example.yaml')
    calendar.main(ROOT/'docs/templates/settlement_calendar.example.yaml')
    lineage.main(ROOT/'docs/templates/data_lineage.example.json')
    rules.main(ROOT/'docs/templates/decision_rule_manifest.example.json')
    deps.main(ROOT/'docs/templates/third_party_dependencies.example.yaml')
    decommission.main(ROOT/'docs/templates/decommission_plan.example.yaml')
    if autoscaling.main(ROOT/'docs/templates/autoscaling_policy.example.yaml',ROOT/'docs/templates/hpa.phase43-52.example.yaml')!=0: raise SystemExit('autoscaling guardrail harness failed')
    if golden.main(ROOT/'docs/templates/decision_rule_golden_cases.example.json',ROOT/'docs/templates/decision_rule_golden_results.example.json')!=0: raise SystemExit('golden rule harness failed')
with tempfile.TemporaryDirectory() as td:
    d=pathlib.Path(td); out=d/'forecast.json'
    with contextlib.redirect_stdout(io.StringIO()):
        capacity.main(ROOT/'docs/templates/capacity_observations.example.json',out,30,'500')
    result=json.loads(out.read_text(encoding='utf-8'))
    if result['required_replicas']<1 or not re.fullmatch(r'[0-9a-f]{64}',result['evidence_sha256']): raise SystemExit('capacity forecast harness failed')
    artifact=d/'evidence.txt';artifact.write_text('phase43-52 evidence\n',encoding='utf-8')
    manifest={'artifacts':[{'path':'evidence.txt','sha256':hashlib.sha256(artifact.read_bytes()).hexdigest(),'size_bytes':artifact.stat().st_size}]}
    (d/'manifest.json').write_text(json.dumps(manifest),encoding='utf-8')
    with contextlib.redirect_stdout(io.StringIO()):
        if evidence.main(d/'manifest.json',d)!=0: raise SystemExit('evidence verification harness failed')

# Negative cases must fail closed
def must_raise(fn,*args):
    try: fn(*args)
    except (SystemExit,ValueError): return
    raise SystemExit('negative harness unexpectedly passed: '+fn.__module__+'.'+fn.__name__)
with tempfile.TemporaryDirectory() as td:
    d=pathlib.Path(td)
    bad=d/'bad.yaml';bad.write_text('version: 1\nentitlements: []\nlimits: []\n',encoding='utf-8')
    with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
        must_raise(limits.main,bad)
    cycle=d/'cycle.json';cycle.write_text(json.dumps({'assets':[{'code':'A','type':'TABLE','reference':'a','owner':'x','classification':'INTERNAL'},{'code':'B','type':'TABLE','reference':'b','owner':'x','classification':'INTERNAL'}],'edges':[{'source':'A','target':'B','transformation':'x','version':'1','purpose':'x'},{'source':'B','target':'A','transformation':'x','version':'1','purpose':'x'}]}),encoding='utf-8')
    with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
        must_raise(lineage.main,cycle)
print('Phase 43-52 static acceptance: PASS')
