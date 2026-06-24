#!/usr/bin/env python3
from pathlib import Path
import json, py_compile, subprocess, sys, yaml
root=Path(__file__).resolve().parents[1]
required=['AGENT/PHASE76A-76J_CHECKLIST.md','scripts/phase76/run_phase76.sh','config/phase76/evidence-source-registry.yaml','schemas/phase76/evidence-ledger.schema.json','docs/phase76/PHASE76_OVERVIEW.md','src/main/java/com/example/switching/readiness/controller/ReadinessController.java','src/main/java/com/example/switching/readiness/service/EvidenceLedgerService.java','scripts/verify_phase76_77_boundary.py','scripts/validate_phase76_77_artifacts.py']
errors=[]
for r in required:
    if not (root/r).is_file(): errors.append('missing:'+r)
for p in list((root/'scripts/phase76').glob('*.py')) + [root/'scripts/verify_phase76_77_boundary.py', root/'scripts/validate_phase76_77_artifacts.py']:
    try: py_compile.compile(str(p),doraise=True)
    except Exception as e: errors.append(f'python:{p}:{e}')
for p in (root/'scripts/phase76').glob('*.sh'):
    if subprocess.run(['bash','-n',str(p)]).returncode: errors.append('shell:'+str(p))
for p in (root/'schemas/phase76').glob('*.json'):
    try: json.loads(p.read_text())
    except Exception as e: errors.append(f'json:{p}:{e}')
for p in (root/'config/phase76').glob('*.yaml'):
    try: yaml.safe_load(p.read_text())
    except Exception as e: errors.append(f'yaml:{p}:{e}')
controller=(root/'src/main/java/com/example/switching/readiness/controller/ReadinessController.java').read_text() if (root/'src/main/java/com/example/switching/readiness/controller/ReadinessController.java').is_file() else ''
if '@PreAuthorize' not in controller or 'ConditionalOnProperty' not in controller: errors.append('readiness API must be RBAC and feature gated')
print(json.dumps({'status':'PASS' if not errors else 'FAIL','errors':errors},indent=2)); sys.exit(1 if errors else 0)
