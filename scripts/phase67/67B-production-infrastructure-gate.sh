#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
p67_require_identity
PHASE_ID=67B
p67_require_environment production
p67_require_production_confirmation
p67_begin 67B "Production Infrastructure Contract Gate"
failed=0
if [[ "$PHASE67_MODE" == preflight ]]; then
  p67_run_check tooling-ready bash -c 'test -f scripts/golive/55b-production-infrastructure-contract.sh && test -f config/production-infrastructure-contract.yaml; exit 3' || failed=1
else
  p67_run_check phase55b-pass p67_require_phase55_pass 55B || failed=1
  p67_run_check infrastructure-evidence python3 - "$PHASE55_ROOT/phases/55B" "$PHASE_DIR/infrastructure-gate.json" <<'PY' || failed=1
import json, pathlib, sys
root=pathlib.Path(sys.argv[1]); out=pathlib.Path(sys.argv[2])
required=['infrastructure-contract.json','tls-report.json','connectivity-report.json','environment-contract.txt']
errors=[]
for name in required:
    p=root/name
    if not p.is_file() or p.is_symlink(): errors.append(f'missing or unsafe {name}')
for name in required[:3]:
    p=root/name
    if p.is_file():
        try: json.loads(p.read_text(encoding='utf-8'))
        except Exception: errors.append(f'invalid JSON {name}')
doc={'schemaVersion':1,'status':'PASS' if not errors else 'FAIL','sourcePhase':'55B','requiredArtifacts':required,'errors':errors}
out.write_text(json.dumps(doc,indent=2,sort_keys=True)+'\n',encoding='utf-8')
raise SystemExit(0 if not errors else 2)
PY
fi
if (( failed )); then p67_write_result FAIL; exit 1; fi
p67_write_result
