#!/usr/bin/env python3
from __future__ import annotations
import argparse, fnmatch, json, py_compile, subprocess, sys, tempfile
from pathlib import Path
try:
    import yaml
except ImportError:
    yaml=None

ROOT=Path(__file__).resolve().parents[1]
REQUIRED=[
 'AGENT/PHASE69A-69J_CHECKLIST.md',
 *(f'scripts/phase69/69{x}-' + name for x,name in zip('ABCDEFGHIJ',[
  'phase68-handoff-collision-guard.sh','webhook-objectmapper-closure.sh','operations-fk-cleanup-closure.sh',
  'crossborder-temporal-binding-closure.sh','regression-test-certification.sh','targeted-test-evidence.sh',
  'full-maven-verification.sh','repository-gate-verification.sh','migration-config-regression.sh','build-verification-bundle.sh'])),
 'scripts/phase69/common.sh','scripts/phase69/run_phase69.sh','scripts/phase69/write_phase_result.py',
 'scripts/phase69/collect_junit_results.py','scripts/phase69/verify_crossborder_temporal_binding.py',
 'scripts/phase69/build_verification_manifest.py','scripts/verify_phase69_static.py',
 'scripts/execute-and-verify/11-phase69-verification-closure.sh',
 'config/phase69/verification-policy.yaml','schemas/phase69/phase69-result.schema.json',
 'schemas/phase69/phase69-verification-manifest.schema.json','docs/phase69/PHASE69_IMPLEMENTATION.md',
 'docs/phase69/PHASE69_OPERATOR_RUNBOOK.md','docs/phase69/PHASE69_EXIT_CRITERIA.md',
 'docs/templates/PHASE69_RELEASE_VERIFICATION_ATTESTATION.example.json',
 '.github/workflows/phase69-verification-closure.yml',
 'src/main/java/com/example/switching/webhook/crypto/WebhookEncryptionConfiguration.java',
 'src/test/java/com/example/switching/webhook/crypto/WebhookEncryptionConfigurationTest.java',
 'src/test/java/com/example/switching/operations/service/OperationsGenerateRoutesForBankIntegrationTest.java']

def load_policy():
    path=ROOT/'config/phase69/verification-policy.yaml'
    if yaml is None:
        raise RuntimeError('PyYAML is required for Phase 69 static verification')
    return yaml.safe_load(path.read_text(encoding='utf-8'))

def changed_paths():
    paths=set()
    for cmd in (['git','diff','--name-only'],['git','diff','--cached','--name-only'],['git','ls-files','--others','--exclude-standard']):
        r=subprocess.run(cmd,cwd=ROOT,text=True,capture_output=True)
        if r.returncode==0: paths.update(x for x in r.stdout.splitlines() if x)
    return sorted(paths)

def prefix_match(path,prefix):
    return path==prefix or path.startswith(prefix.rstrip('/')+'/') or (prefix.endswith('/') and path.startswith(prefix))

def boundary_failures(policy):
    own=policy['ownership']; failures=[]
    allowed_prefixes=own['allowedPhase69Prefixes']; allowed_files=set(own['allowedSourceFiles']); inherited=own['inheritedDirtyPaths']; forbidden=own['forbiddenPrefixes']
    for path in changed_paths():
        if any(prefix_match(path,x) for x in forbidden): failures.append(f'Phase 68-owned path modified: {path}'); continue
        if path in allowed_files or any(prefix_match(path,x) for x in allowed_prefixes) or any(prefix_match(path,x) for x in inherited): continue
        failures.append(f'changed path outside Phase 69 boundary: {path}')
    return failures

def main():
    p=argparse.ArgumentParser(); p.add_argument('--boundary-only',action='store_true'); a=p.parse_args()
    failures=[]
    try: policy=load_policy()
    except Exception as e:
        print(f'Phase 69 static contract: FAIL\n  ERROR: {e}'); return 1
    failures.extend(boundary_failures(policy))
    if a.boundary_only:
        if failures:
            print(f'Phase 69 collision boundary: FAIL ({len(failures)} issues)')
            for x in failures: print('  ERROR:',x)
            return 1
        print('Phase 69 collision boundary: PASS'); return 0
    for rel in REQUIRED:
        if not (ROOT/rel).is_file(): failures.append(f'missing {rel}')
    for path in sorted((ROOT/'scripts/phase69').glob('*.sh'))+[ROOT/'scripts/execute-and-verify/11-phase69-verification-closure.sh']:
        if path.is_file():
            r=subprocess.run(['bash','-n',str(path)],cwd=ROOT,text=True,capture_output=True)
            if r.returncode: failures.append(f'{path.relative_to(ROOT)} bash syntax: {r.stderr.strip()}')
            if not path.stat().st_mode & 0o100: failures.append(f'{path.relative_to(ROOT)} is not executable')
    with tempfile.TemporaryDirectory() as temp:
        for i,path in enumerate(sorted([*ROOT.glob('scripts/phase69/*.py'), ROOT/'scripts/verify_phase69_static.py'])):
            if path.is_file():
                try: py_compile.compile(str(path),cfile=str(Path(temp)/f'{i}.pyc'),doraise=True)
                except Exception as e: failures.append(f'{path.relative_to(ROOT)} Python compile: {e}')
    for rel in ('schemas/phase69/phase69-result.schema.json','schemas/phase69/phase69-verification-manifest.schema.json','docs/templates/PHASE69_RELEASE_VERIFICATION_ATTESTATION.example.json'):
        try: json.loads((ROOT/rel).read_text(encoding='utf-8'))
        except Exception as e: failures.append(f'{rel} invalid JSON: {e}')
    source=(ROOT/'src/main/java/com/example/switching/webhook/crypto/WebhookEncryptionConfiguration.java').read_text(encoding='utf-8')
    for marker in ('webhookEncryptionObjectMapper()','@ConditionalOnMissingBean(ObjectMapper.class)','JsonMapper.builder()'):
        if marker not in source: failures.append(f'WebhookEncryptionConfiguration missing {marker!r}')
    ops=(ROOT/'src/test/java/com/example/switching/operations/service/OperationsGenerateRoutesForBankIntegrationTest.java').read_text(encoding='utf-8')
    if 'deactivateConnectorlessParticipants()' not in ops or "SET status = 'INACTIVE'" not in ops: failures.append('operations FK-safe isolation fix missing')
    if 'DELETE FROM participants' in ops: failures.append('operations integration test still deletes participants')
    temporal=subprocess.run([sys.executable,str(ROOT/'scripts/phase69/verify_crossborder_temporal_binding.py'),'--self-test'],cwd=ROOT,text=True,capture_output=True)
    if temporal.returncode: failures.append('cross-border temporal guard failed: '+temporal.stdout+temporal.stderr)
    workflow=(ROOT/'.github/workflows/phase69-verification-closure.yml').read_text(encoding='utf-8') if (ROOT/'.github/workflows/phase69-verification-closure.yml').is_file() else ''
    for marker in ('verify_phase69_static.py','run_phase69.sh --preflight','mvnw -B clean verify','upload-artifact'):
        if marker not in workflow: failures.append(f'workflow missing {marker!r}')
    diff=subprocess.run(['git','diff','--check'],cwd=ROOT,text=True,capture_output=True)
    if diff.returncode: failures.append('git diff --check failed: '+diff.stdout.strip())
    if failures:
        print(f'Phase 69 static contract: FAIL ({len(failures)} issues)')
        for x in failures: print('  ERROR:',x)
        return 1
    print('Phase 69 static contract: PASS')
    return 0
if __name__=='__main__': raise SystemExit(main())
