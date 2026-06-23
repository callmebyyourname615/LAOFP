#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE64_ROOT"
phase64_setup "64J" "Signed UAT evidence handoff bundle"
STATUS=FAIL; MESSAGE="signed UAT handoff bundle failed"
trap 'code=$?; phase64_finalize "$STATUS" "$code" "$MESSAGE"' EXIT
phase64_require_file scripts/phase64/build_signed_bundle.py
phase64_require_file docs/templates/PHASE64_HANDOFF_APPROVAL.example.json
phase64_require_command openssl
if phase64_is_preflight; then
  phase64_run "validate handoff approval template" python3 -m json.tool docs/templates/PHASE64_HANDOFF_APPROVAL.example.json
  phase64_run "check OpenSSL signing support" openssl version
  STATUS=PREPARED; MESSAGE="bundle, checksum and OpenSSL signature tooling are ready"; exit 0
fi
phase64_require_release_identity
: "${PHASE64_HANDOFF_APPROVAL:?PHASE64_HANDOFF_APPROVAL is required}"
: "${PHASE64_SIGNING_KEY:?PHASE64_SIGNING_KEY is required}"
: "${PHASE64_SIGNING_PUBLIC_KEY:?PHASE64_SIGNING_PUBLIC_KEY is required}"
phase64_require_file "$PHASE64_HANDOFF_APPROVAL"
phase64_require_file "$PHASE64_SIGNING_KEY"
phase64_require_file "$PHASE64_SIGNING_PUBLIC_KEY"
decision="$PHASE64_RUN_DIR/64I/phase54-entry-decision.json"
phase64_require_file "$decision"
phase64_run "require approved machine gate" python3 - "$decision" <<'PY'
import json,pathlib,sys
data=json.loads(pathlib.Path(sys.argv[1]).read_text())
assert data.get('decision')=='APPROVE_PHASE54_ENTRY', data.get('errors')
print('Machine gate approval: PASS')
PY
phase64_run "build standalone Phase 64 bundle" python3 scripts/phase64/build_signed_bundle.py \
  --run-dir "$PHASE64_RUN_DIR" --approval "$PHASE64_HANDOFF_APPROVAL" \
  --reference "$RELEASE_REFERENCE" --commit "$RELEASE_GIT_COMMIT" \
  --application-digest "$APPLICATION_IMAGE_DIGEST" --migration-digest "$MIGRATION_IMAGE_DIGEST" \
  --output-dir "$PHASE64_PHASE_DIR"
manifest="$PHASE64_PHASE_DIR/bundle-manifest.json"
signature="$PHASE64_PHASE_DIR/bundle-manifest.sig"
phase64_run "sign evidence manifest" openssl dgst -sha256 -sign "$PHASE64_SIGNING_KEY" -out "$signature" "$manifest"
phase64_run "verify evidence manifest signature" openssl dgst -sha256 -verify "$PHASE64_SIGNING_PUBLIC_KEY" -signature "$signature" "$manifest"
cp "$PHASE64_SIGNING_PUBLIC_KEY" "$PHASE64_PHASE_DIR/signing-public-key.pem"
phase64_require_command zip
phase64_run "append signature material to bundle" zip -q -j "$PHASE64_PHASE_DIR/phase64-uat-evidence-bundle.zip" \
  "$signature" "$PHASE64_PHASE_DIR/signing-public-key.pem"
if command -v sha256sum >/dev/null 2>&1; then
  sha256sum "$PHASE64_PHASE_DIR/phase64-uat-evidence-bundle.zip" > "$PHASE64_PHASE_DIR/bundle.sha256"
else
  shasum -a 256 "$PHASE64_PHASE_DIR/phase64-uat-evidence-bundle.zip" > "$PHASE64_PHASE_DIR/bundle.sha256"
fi
STATUS=PASS; MESSAGE="signed and verified Phase 54 entry handoff bundle created"
