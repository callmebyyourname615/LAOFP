#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
p67_require_identity
PHASE_ID=67D
p67_require_environment production
p67_require_production_confirmation
p67_begin 67D "Financial Cutover Baseline Collector"
failed=0
if [[ "$PHASE67_MODE" == preflight ]]; then
  p67_run_check tooling-ready bash -c 'test -f scripts/golive/55d-capture-cutover-baseline.sh && test -f config/phase55-reconciliation-queries.yaml; exit 3' || failed=1
else
  p67_run_check phase55d-pass p67_require_phase55_pass 55D || failed=1
  p67_run_check baseline-integrity python3 - "$PHASE55_ROOT/phases/55D" "$PHASE_DIR/financial-baseline-gate.json" <<'PY' || failed=1
import hashlib,json,pathlib,sys
root=pathlib.Path(sys.argv[1]); out=pathlib.Path(sys.argv[2]); errors=[]
baseline=root/'cutover-baseline.json'; checksum=root/'cutover-baseline.sha256'; receipt=root/'archive-receipt.json'
for p in (baseline,checksum,receipt):
 if not p.is_file() or p.is_symlink(): errors.append(f'missing or unsafe {p.name}')
if baseline.is_file():
 try:
  data=json.loads(baseline.read_text()); results=data.get('results',{})
  for name,item in results.items():
   if item.get('mode')=='balanced':
    for row in item.get('rows',[]):
     if str(row.get('debit','0')) != str(row.get('credit','0')): errors.append(f'unbalanced baseline: {name}')
   if item.get('mode')=='zero':
    for row in item.get('rows',[]):
     if int(row.get('count',0)) != 0: errors.append(f'non-zero control: {name}')
 except Exception as exc: errors.append(f'invalid baseline: {type(exc).__name__}')
if baseline.is_file() and checksum.is_file():
 expected=checksum.read_text().split()[0]; actual=hashlib.sha256(baseline.read_bytes()).hexdigest()
 if expected!=actual: errors.append('baseline checksum mismatch')
doc={'schemaVersion':1,'status':'PASS' if not errors else 'FAIL','sourcePhase':'55D','baselineSha256':hashlib.sha256(baseline.read_bytes()).hexdigest() if baseline.is_file() else None,'errors':errors}
out.write_text(json.dumps(doc,indent=2,sort_keys=True)+'\n')
raise SystemExit(0 if not errors else 2)
PY
fi
if (( failed )); then p67_write_result FAIL; exit 1; fi
p67_write_result
