#!/usr/bin/env python3
from pathlib import Path
import json,sys,yaml
ROOT=Path(__file__).resolve().parents[1]
errors=[]
def need(p):
 q=ROOT/p
 if not q.is_file(): errors.append(f'missing {p}')
 return q
plan=yaml.safe_load(need('config/phase56-day2-plan.yaml').read_text())
expected=[f'56{x}' for x in 'ABCDEFGHIJ']
ids=[x['id'] for x in plan.get('phases',[])]
if ids!=expected: errors.append(f'phase order mismatch: {ids}')
for ph in plan['phases']:
 r=need(ph['runner'])
 if r.is_file() and ph['id'] not in r.read_text():errors.append(f"runner does not identify {ph['id']}")
 for ev in ph['evidence']:
  if not ev.startswith(f"phases/{ph['id']}/"):errors.append(f"evidence path mismatch {ev}")
for p in ['config/phase56-thresholds.yaml','slo/slo-catalog.yaml','slo/error-budget-policy.yaml','reconciliation/rules/continuous-integrity.yaml','ha/ha-policy.yaml','capacity/capacity-policy.yaml','compliance/phase56-controls.yaml','progressive-delivery/policy.yaml','resilience/scenario-catalog.yaml']:
 yaml.safe_load(need(p).read_text())
for p in ['schemas/phase56-result.schema.json','schemas/phase56-resilience-certificate.schema.json','schemas/phase56-compliance-evidence.schema.json']:
 json.loads(need(p).read_text())
sql=need('reconciliation/sql/continuous-reconciliation.sql').read_text()
for token in ['REPEATABLE READ READ ONLY','pg_try_advisory_xact_lock','statement_timeout','lock_timeout']:
 if token not in sql: errors.append(f'reconciliation SQL missing {token}')
for f in (ROOT/'monitoring/prometheus').glob('phase56-*.yaml'):
 data=yaml.safe_load(f.read_text())
 for g in data.get('spec',{}).get('groups',[]):
  for r in g.get('rules',[]):
   if 'alert' in r and not r.get('annotations',{}).get('runbook_url'):errors.append(f'{f}: alert missing runbook')
for f in (ROOT/'security/detections').glob('*.yaml'):
 for r in yaml.safe_load(f.read_text()).get('rules',[]):
  if not need(r['runbook']).is_file():errors.append(f"missing runbook {r['runbook']}")
for p in ['k8s/day2/networkpolicy.yaml','k8s/day2/hpa-patch.yaml','k8s/day2/vpa-recommendation.yaml','k8s/day2/deployment-ha-patch.yaml']:
 text=need(p).read_text()
 if '0.0.0.0/0' in text or '::/0' in text:errors.append(f'open CIDR prohibited in {p}')

# Day-2 scheduled execution must preserve evidence immutably and avoid a 56F<->56J cycle.
store=need('scripts/day2/evidence_store.sh').read_text()
for token in ['Object Lock must be Enabled','COMPLIANCE mode','--sse aws:kms','scan_evidence.py']:
 if token not in store: errors.append(f'evidence store missing {token}')
if '--delete' in store: errors.append('evidence store must not delete immutable remote evidence')
for p in ['.github/workflows/phase56-day2-operations.yml','.github/workflows/phase56-ha-failover.yml','.github/workflows/phase56-security-operations.yml','.github/workflows/phase56-compliance-schedule.yml','.github/workflows/phase56-resilience-schedule.yml']:
 text=need(p).read_text()
 if 'evidence_store.sh pull' not in text or 'evidence_store.sh push' not in text: errors.append(f'{p} must pull and push immutable evidence')
pre_controls=yaml.safe_load(need('compliance/phase56-controls.yaml').read_text())
if any(c.get('id')=='RESILIENCE-001' for c in pre_controls.get('controls',[])): errors.append('Phase 56F controls must not depend on the future 56J certificate')
post_controls=yaml.safe_load(need('compliance/post-resilience-controls.yaml').read_text())
if not any(c.get('id')=='RESILIENCE-001' for c in post_controls.get('controls',[])): errors.append('post-resilience compliance control missing')
need('compliance/post-resilience-evidence-mapping.yaml')

if errors:
 print('\n'.join('ERROR: '+x for x in errors));sys.exit(1)
print('Phase 56 static contract: OK')
