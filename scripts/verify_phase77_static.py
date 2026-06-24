#!/usr/bin/env python3
from pathlib import Path
import json, py_compile, subprocess, sys, yaml
root=Path(__file__).resolve().parents[1]; errors=[]
required=['AGENT/PHASE77A-77J_CHECKLIST.md','scripts/phase77/run_phase77.sh','docs/phase77/PHASE77_OVERVIEW.md','config/phase77/scorecard-policy.yaml','src/main/java/com/example/switching/continuousassurance/controller/ContinuousAssuranceController.java','sql/phase77/continuous_financial_reconciliation.sql']
for r in required:
    if not (root/r).is_file(): errors.append('missing:'+r)
for p in (root/'scripts/phase77').glob('*.py'):
    try: py_compile.compile(str(p),doraise=True)
    except Exception as e: errors.append(f'python:{p}:{e}')
for p in (root/'scripts/phase77').glob('*.sh'):
    if subprocess.run(['bash','-n',str(p)]).returncode: errors.append('shell:'+str(p))
for p in (root/'schemas/phase77').glob('*.json'):
    try: json.loads(p.read_text())
    except Exception as e: errors.append(f'json:{p}:{e}')
for p in (root/'config/phase77').glob('*.yaml'):
    try: yaml.safe_load(p.read_text())
    except Exception as e: errors.append(f'yaml:{p}:{e}')
controller=(root/'src/main/java/com/example/switching/continuousassurance/controller/ContinuousAssuranceController.java').read_text() if (root/'src/main/java/com/example/switching/continuousassurance/controller/ContinuousAssuranceController.java').is_file() else ''
if '@PreAuthorize' not in controller or 'ConditionalOnProperty' not in controller: errors.append('continuous assurance API must be RBAC and feature gated')
print(json.dumps({'status':'PASS' if not errors else 'FAIL','errors':errors},indent=2)); sys.exit(1 if errors else 0)
