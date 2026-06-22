#!/usr/bin/env python3
from pathlib import Path
import yaml,json,re,sys
ROOT=Path(__file__).resolve().parents[1]; errors=[]
def need(x):
 p=ROOT/x
 if not p.is_file(): errors.append(f'missing {x}')
 return p
plan=yaml.safe_load(need('config/phase58-assurance-plan.yaml').read_text()); expected=[f'58{x}' for x in 'ABCDEFGHIJ']; phases=plan.get('phases',[]); ids=[x.get('id') for x in phases]
if ids!=expected: errors.append(f'phase ids/order mismatch: {ids}')
by={x['id']:x for x in phases}
for p in phases:
 rp=need(p['runner'])
 if rp.exists() and not rp.stat().st_mode & 0o111: errors.append(f'runner not executable {p["runner"]}')
 if f"phases/{p['id']}/result.json" not in p.get('evidence',[]): errors.append(f"{p['id']} result evidence missing")
 for d in p.get('prerequisites',[]):
  if d not in expected or expected.index(d)>=expected.index(p['id']): errors.append(f'invalid prerequisite {d} for {p["id"]}')
required=['config/phase58-thresholds.yaml','scripts/assurance/common.sh','scripts/assurance/run_phase58_assurance.sh','scripts/assurance/verify_input_identity.py','scripts/assurance/build_evidence_manifest.py','scripts/assurance/verify_evidence_manifest.py','scripts/assurance/sign_and_verify_blob.sh','scripts/assurance/evidence_store.sh','scripts/security/scan_repository_secrets.py','scripts/security/scan_repository_secrets.sh','regulatory-assurance/report-catalog.yaml','participant-governance/certification-policy.yaml','crypto-agility/algorithm-policy.yaml','privacy-engineering/privacy-control-policy.yaml','decision-governance/model-rule-policy.yaml','iso20022/market-practice-lifecycle.yaml','settlement-risk/liquidity-risk-policy.yaml','digital-twin/scenario-policy.yaml','third-party-risk/vendor-risk-policy.yaml','supervisory-readiness/certification-policy.yaml']
for x in required: need(x)
for pid in expected[:-1]:
 txt=need(by[pid]['runner']).read_text()
 if 'verify_input_identity.py' not in txt: errors.append(f'{pid} does not bind input identity')
alg=yaml.safe_load(need('crypto-agility/algorithm-policy.yaml').read_text())
for weak in ['MD5','SHA-1','RSA-1024','3DES']:
 if weak not in alg['prohibited']: errors.append(f'weak algorithm not prohibited: {weak}')
ret=yaml.safe_load(need('privacy-engineering/rights-case-policy.yaml').read_text())
if not ret.get('legalHoldOverridesErasure') or ret.get('caseSlaHours',999)>72: errors.append('privacy rights policy weak')
liq=yaml.safe_load(need('settlement-risk/liquidity-risk-policy.yaml').read_text())
if not liq.get('zeroSettlementMismatchRequired'): errors.append('settlement mismatch tolerance not zero')
dtwin=yaml.safe_load(need('digital-twin/scenario-policy.yaml').read_text())
if not dtwin.get('productionSideEffectsProhibited') or dtwin.get('minimumCoveragePercent')!=100: errors.append('digital twin safety policy weak')
cert=yaml.safe_load(need('supervisory-readiness/certification-policy.yaml').read_text())
if not cert.get('requireAllPhase58ResultsPass') or not cert.get('requireSignedEvidenceManifest'): errors.append('supervisory certification not fail-closed')
jrunner=need(by['58J']['runner']).read_text()
for token in ['manifest-signature','sign_and_verify_blob.sh','evidence-manifest.sig']:
 if token not in jrunner: errors.append(f'58J missing signed manifest control: {token}')
store=need('scripts/assurance/evidence_store.sh').read_text()
for token in ['Object Lock COMPLIANCE','--server-side-encryption aws:kms','--object-lock-mode COMPLIANCE']:
 if token not in store: errors.append(f'evidence store missing {token}')
if re.search(r'aws\s+.*\bs3\s+(rm|mv)\b',store): errors.append('destructive evidence-store operation found')
for p in ROOT.glob('scripts/assurance/*.sh'):
 txt=p.read_text()
 if 'eval ' in txt or 'bash -c' in txt or 'sh -c' in txt: errors.append(f'arbitrary command pattern in {p.relative_to(ROOT)}')
for x in ['.github/workflows/phase58-assurance-operations.yml','.github/workflows/phase58-supervisory-readiness.yml','.github/workflows/phase58-static-contract.yml','docs/runbooks/PHASE58_REGULATORY_ECOSYSTEM_ASSURANCE.md']:
 need(x)
if errors:
 print('\n'.join('ERROR: '+x for x in errors)); raise SystemExit(1)
print('Phase 58 static verification OK')
