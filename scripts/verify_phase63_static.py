#!/usr/bin/env python3
from __future__ import annotations
import json, subprocess, sys
from pathlib import Path
import yaml
ROOT=Path(__file__).resolve().parents[1]
REQUIRED=[
 'scripts/phase63/common.sh','scripts/phase63/run_phase63.sh','scripts/phase63/write_phase_result.py',
 *[f'scripts/phase63/63{letter}-{name}.sh' for letter,name in zip('ABCDEFGHIJ',[
 'uat-environment-inventory','phase61-preflight-execution','secret-rotation-ceremony','performance-capacity-execution','backup-pitr-execution','dr-scenario-execution','observability-alert-validation','smos-rbac-audit','reconciliation-sanctions-execution','uat-evidence-entry-gate'])],
 'scripts/phase63/verify_phase61_compatibility.py','scripts/phase63/phase61A-current-build-test-closure.sh','scripts/phase63/phase61B-current-migration-certification.sh','scripts/phase63/verify_phase63_evidence_tools.py','scripts/execute-and-verify/08-phase63-preflight.sh','.github/workflows/phase63-certification.yml','config/phase63/execution.env.example','docs/phase63/README.md','docs/templates/PHASE63_BACKUP_PITR_ATTESTATION.example.json','docs/templates/PHASE63_DR_ATTESTATION.example.json','docs/templates/PHASE63_ALERT_ATTESTATION.example.json','docs/templates/PHASE63_SANCTIONS_ATTESTATION.example.json','docs/templates/PHASE63_UAT_ENTRY_ATTESTATION.example.json']

def main()->int:
 errors=[]
 for rel in REQUIRED:
  if not (ROOT/rel).is_file(): errors.append(f'missing {rel}')
 for path in sorted((ROOT/'scripts/phase63').glob('*.sh')):
  proc=subprocess.run(['bash','-n',str(path)],capture_output=True,text=True)
  if proc.returncode: errors.append(f'bash syntax {path.relative_to(ROOT)}: {proc.stderr.strip()}')
 for path in sorted((ROOT/'scripts/phase63').glob('*.py')):
  try: compile(path.read_text(encoding='utf-8'),str(path),'exec')
  except SyntaxError as exc: errors.append(f'python syntax {path.relative_to(ROOT)}:{exc.lineno}: {exc.msg}')
 for path in sorted((ROOT/'docs/templates').glob('PHASE63_*.json')):
  try: json.loads(path.read_text(encoding='utf-8'))
  except Exception as exc: errors.append(f'JSON {path.relative_to(ROOT)}: {exc}')
 try: yaml.safe_load((ROOT/'config/phase61-uat-infrastructure-contract.yaml').read_text(encoding='utf-8'))
 except Exception as exc: errors.append(f'phase61 UAT contract YAML: {exc}')
 run=(ROOT/'scripts/phase63/run_phase63.sh').read_text(encoding='utf-8')
 for letter in 'ABCDEFGHIJ':
  if f'63{letter}-' not in run: errors.append(f'orchestrator missing 63{letter}')

 proc=subprocess.run([sys.executable,str(ROOT/'scripts/monitoring/verify_alert_runbooks.py')],cwd=ROOT,text=True,capture_output=True)
 if proc.returncode: errors.append(f'alert/runbook contract: {proc.stdout.strip()} {proc.stderr.strip()}')
 if errors:
  print(f'Phase 63 static contract: FAIL ({len(errors)} issue(s))',file=sys.stderr)
  for e in errors: print('  -',e,file=sys.stderr)
  return 1
 print('Phase 63 static contract: PASS')
 return 0
if __name__=='__main__': raise SystemExit(main())
