#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
p67_require_identity
PHASE_ID=67C
p67_require_environment release
p67_begin 67C "Immutable Release Candidate and Provenance Verification"
failed=0
if [[ "$PHASE67_MODE" == preflight ]]; then
  p67_run_check tooling-ready bash -c 'test -f scripts/golive/55a-assemble-release-candidate.sh && test -f scripts/golive/verify_release_candidate.py; exit 3' || failed=1
else
  p67_run_check phase55a-pass p67_require_phase55_pass 55A || failed=1
  p67_run_check rc-integrity python3 - "$PHASE55_ROOT/phases/55A" "$PHASE_DIR/rc-provenance-gate.json" <<'PY' || failed=1
import hashlib,json,pathlib,sys
root=pathlib.Path(sys.argv[1]); out=pathlib.Path(sys.argv[2]); errors=[]
required=['release-candidate/manifest.json','release-candidate/checksums.sha256','release-candidate-verification.json','release-candidate.tar.gz','release-candidate.tar.gz.sha256','release-package-signature-verification.txt']
for rel in required:
 p=root/rel
 if not p.is_file() or p.is_symlink(): errors.append(f'missing or unsafe {rel}')
archive=root/'release-candidate.tar.gz'; checksum=root/'release-candidate.tar.gz.sha256'
if archive.is_file() and checksum.is_file():
 expected=checksum.read_text().split()[0]; actual=hashlib.sha256(archive.read_bytes()).hexdigest()
 if expected!=actual: errors.append('release archive checksum mismatch')
verify=root/'release-package-signature-verification.txt'
if verify.is_file() and not any(token in verify.read_text(errors='replace').lower() for token in ('verified','verification succeeded','ok')):
 errors.append('signature verification evidence is not affirmative')
doc={'schemaVersion':1,'status':'PASS' if not errors else 'FAIL','sourcePhase':'55A','requiredArtifacts':required,'errors':errors}
out.write_text(json.dumps(doc,indent=2,sort_keys=True)+'\n')
raise SystemExit(0 if not errors else 2)
PY
fi
if (( failed )); then p67_write_result FAIL; exit 1; fi
p67_write_result
