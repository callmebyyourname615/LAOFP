#!/usr/bin/env python3
from pathlib import Path
import re,sys,yaml,json
ROOT=Path(__file__).resolve().parents[1]; errors=[]
def need(path):
    p=ROOT/path
    if not p.is_file(): errors.append(f'missing {path}')
    return p
plan= yaml.safe_load(need('config/phase57-enterprise-plan.yaml').read_text())
expected=[f'57{x}' for x in 'ABCDEFGHIJ']; phases=plan.get('phases',[]); ids=[x.get('id') for x in phases]
if ids!=expected: errors.append(f'phase ids/order mismatch: {ids}')
allowed=set(plan.get('allowedExecutionEnvironments',[]))
if allowed != {'production','operations','security','compliance','dr','financial-control'}: errors.append('allowed environments mismatch')
by_id={x['id']:x for x in phases}
for phase in phases:
    pid=phase['id']; runner=phase.get('runner','')
    rp=need(runner)
    if rp.exists() and not (rp.stat().st_mode & 0o111): errors.append(f'runner not executable: {runner}')
    if not set(phase.get('environments',[])).issubset(allowed): errors.append(f'invalid environment in {pid}')
    for dep in phase.get('prerequisites',[]):
        if dep not in expected or expected.index(dep)>=expected.index(pid): errors.append(f'invalid prerequisite {dep} for {pid}')
    ev=phase.get('evidence',[])
    if not ev or f'phases/{pid}/result.json' not in ev: errors.append(f'{pid} result evidence missing')
    if len(ev)!=len(set(ev)): errors.append(f'duplicate evidence path in {pid}')
# detect cycles
vis=set(); stack=set()
def visit(pid):
    if pid in stack: errors.append(f'dependency cycle at {pid}'); return
    if pid in vis:return
    stack.add(pid)
    for d in by_id[pid].get('prerequisites',[]): visit(d)
    stack.remove(pid); vis.add(pid)
for p in expected: visit(p)
required=[
'config/phase57-thresholds.yaml','scripts/enterprise/common.sh','scripts/enterprise/run_phase57_enterprise.sh',
'scripts/enterprise/evidence_store.sh','scripts/enterprise/build_evidence_manifest.py','scripts/enterprise/verify_evidence_manifest.py','scripts/enterprise/sign_and_verify_blob.sh','scripts/enterprise/build_sign_verify_manifest.sh',
'certification/enterprise/impact-rules.yaml','multi-region-dr/topology-policy.yaml','financial-controls/control-catalog.yaml',
'data-governance/retention-policy.yaml','fraud/rule-catalog.yaml','observability/intelligence/anomaly-policy.yaml',
'operations/operation-catalog.yaml','vulnerability-management/severity-sla.yaml','continuity/dependency-catalog.yaml',
'enterprise-certification/maturity-model.yaml','enterprise-certification/certification-policy.yaml']
for p in required: need(p)
for pid in [f'57{x}' for x in 'BCDEFGHI']:
    txt=need(by_id[pid]['runner']).read_text()
    if 'verify_input_identity.py' not in txt: errors.append(f'{pid} does not bind runtime inputs to release identity')
# high-risk invariants
ret=yaml.safe_load(need('data-governance/retention-policy.yaml').read_text())
if ret.get('minimumApproversForDeletion',0)<2 or not ret.get('deleteDryRunRequired'): errors.append('deletion governance is not fail-closed')
ops=yaml.safe_load(need('operations/safety-limits.yaml').read_text())
if not ops.get('arbitraryShellProhibited') or ops.get('maximumDeadLetterRetryBatch',999999)>100: errors.append('operation safety limits too permissive')
dr=yaml.safe_load(need('multi-region-dr/topology-policy.yaml').read_text())
if not dr.get('fencing',{}).get('singleWriterEnforced') or dr.get('failback',{}).get('dataDivergenceTolerance')!=0: errors.append('DR fencing/integrity policy weak')
financial=yaml.safe_load(need('financial-controls/reconciliation-tolerances.yaml').read_text())
if any(v!=0 for k,v in financial.items() if k!='schemaVersion'): errors.append('financial tolerances must be zero')
cert=yaml.safe_load(need('enterprise-certification/certification-policy.yaml').read_text())
if not cert.get('requireAllPhase57ResultsPass') or not cert.get('requireSignedEvidenceManifest'): errors.append('enterprise certificate not fail-closed')
store=need('scripts/enterprise/evidence_store.sh').read_text()
for token in ['Object Lock COMPLIANCE','--sse aws:kms','--no-follow-symlinks']:
    if token not in store: errors.append(f'evidence store missing {token}')
if re.search(r'aws\s+.*\bs3\s+(rm|mv)\b',store): errors.append('destructive remote evidence operation found')
for p in ROOT.glob('scripts/enterprise/*.sh'):
    txt=p.read_text()
    if 'eval ' in txt or 'bash -c' in txt or 'sh -c' in txt: errors.append(f'arbitrary command execution pattern in {p.relative_to(ROOT)}')
# Workflow and docs required
for p in ['.github/workflows/phase57-static-contract.yml','.github/workflows/phase57-enterprise-operations.yml','.github/workflows/phase57-multi-region-dr.yml','.github/workflows/phase57-security-compliance.yml','docs/runbooks/enterprise/PHASE57_MASTER_RUNBOOK.md']:
    need(p)
if errors:
    print('\n'.join(f'ERROR: {x}' for x in errors),file=sys.stderr); raise SystemExit(1)
print(f'OK: Phase 57 static contract ({len(expected)} phases)')
