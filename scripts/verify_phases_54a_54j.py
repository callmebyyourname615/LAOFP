#!/usr/bin/env python3
"""Dependency-light structural verification for Phase 54A-54J certification."""
from __future__ import annotations
import json, pathlib, re, sys
import yaml

ROOT=pathlib.Path(__file__).resolve().parents[1]
errors=[]

def read(path):
    p=ROOT/path
    if not p.is_file(): errors.append(f"missing file: {path}"); return ""
    return p.read_text(encoding='utf-8')

def require(path,*tokens):
    s=read(path)
    for token in tokens:
        if token not in s: errors.append(f"{path}: missing {token!r}")

try:
    plan=yaml.safe_load(read('config/phase54-certification-plan.yaml'))
    ids=[p.get('id') for p in plan.get('phases',[])]
    if ids != [f'54{x}' for x in 'ABCDEFGHIJ']: errors.append(f"phase plan is not ordered 54A-54J: {ids}")
    if not all(p.get('requiredForReleaseCandidate') for p in plan.get('phases',[])): errors.append('all Phase 54 phases must be release-candidate blockers')
except Exception as exc: errors.append(f'invalid Phase 54 plan: {exc}')
try:
    thresholds=yaml.safe_load(read('config/phase54-thresholds.yaml'))
    if thresholds.get('migration',{}).get('expectedFlywayVersion')!='83': errors.append('expected Flyway version must be 83')
    if thresholds.get('settlement',{}).get('requiredTransactionCount')!=500000: errors.append('settlement certification must require 500000 transactions')
    if thresholds.get('canary',{}).get('weights') != [5,25,50,100]: errors.append('canary stages must be 5,25,50,100')
except Exception as exc: errors.append(f'invalid thresholds: {exc}')
for schema in ['schemas/phase54-certification-manifest.schema.json','schemas/phase54-phase-result.schema.json']:
    try: json.loads(read(schema))
    except Exception as exc: errors.append(f'invalid JSON schema {schema}: {exc}')
for letter,name in zip('ABCDEFGHIJ',['build-test','migration','uat-rehearsal','performance','settlement','backup-pitr','dr-recovery','security-supply-chain','observability-alerts','golive-release-candidate']):
    require(f'scripts/certification/54{letter.lower()}-{name}.sh','cert_require_release_identity','write_phase_result')
require('scripts/certification/common.sh','Production execution is intentionally prohibited' if False else 'certification is prohibited in environment')
require('scripts/certification/build_certification_manifest.py','releaseCandidateReady','NOT_RUN','sha256')
require('scripts/certification/build_certification_manifest.py','missingRequiredEvidence','requiredEvidence')
require('scripts/certification/scan_evidence.py','authorization-bearer','findingCount')
require('scripts/certification/create_release_candidate.py','--through','54J')
require('scripts/certification/run_static_gates.py','subprocess.run','timeout')
require('scripts/certification/verify_certification_manifest.py','--require-ready','hash mismatch','54J')
require('scripts/certification/run_phase54_certification.sh','54A 54B 54H 54C 54D 54E 54F 54G 54I 54J','assemble')
require('pom.xml','jacoco-maven-plugin','0.8.13')
require('src/test/java/com/example/switching/migration/V83CleanInstallCertificationIntegrationTest.java','isEqualTo("96")','isEqualTo(96L)','pending()).isEmpty()')
require('performance/scripts/run-k6.sh','RESULT_DIR','result_abs')
require('performance/settlement/run_settlement_benchmark.sh','balanceMismatchCount','SETTLEMENT_SUMMARY_OUTPUT','500000')
require('backup/bin/restore-drill.sh','CERTIFICATION_RESTORE_EVIDENCE')
require('scripts/release/progressive-rollout.sh','service.rendered.yaml','weights','NAMESPACE')
require('.github/workflows/phase54-static-contract.yml','Phase 54 Static Contract')
require('.github/workflows/phase54-certification.yml','switching-certification','retention-days: 365')
require('.github/workflows/phase54-supply-chain.yml','cosign sign','attest-build-provenance','sbom.spdx.json')
require('scripts/configure_branch_protection.sh','Phase 54 Static Contract')
# Shell scripts must be executable in source checkout.
for path in sorted((ROOT/'scripts/certification').glob('*.sh')):
    if path.stat().st_mode & 0o111 == 0: errors.append(f'not executable: {path.relative_to(ROOT)}')
if errors:
    print(f'Phase 54A-54J static verification: FAIL ({len(errors)} issue(s))',file=sys.stderr)
    for e in errors: print(f'  - {e}',file=sys.stderr)
    raise SystemExit(1)
print('Phase 54A-54J static verification: PASS')
