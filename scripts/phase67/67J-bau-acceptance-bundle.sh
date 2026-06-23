#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
p67_require_identity
PHASE_ID=67J
p67_require_environment hypercare
p67_begin 67J "BAU Operational Acceptance Bundle"
failed=0
bundle_dir="$PHASE_DIR/bundle"
if [[ "$PHASE67_MODE" == preflight ]]; then
  p67_run_check bundle-tooling bash -c 'python3 -m py_compile scripts/phase67/phase67_control.py; command -v openssl >/dev/null; exit 3' || failed=1
else
  p67_require_phase67_pass 67A 67B 67C 67D 67E 67F 67G 67H 67I
  p67_run_check phase55j-pass p67_require_phase55_pass 55J || failed=1
  p67_run_check build-bundle python3 scripts/phase67/phase67_control.py bundle \
    --root "$PHASE67_ROOT" --output-dir "$bundle_dir" \
    --reference "$RELEASE_REFERENCE" --rc-id "$RELEASE_RC_ID" --git-commit "$RELEASE_GIT_COMMIT" \
    --application-digest "$RELEASE_APP_IMAGE_DIGEST" --migration-digest "$RELEASE_MIGRATION_IMAGE_DIGEST" \
    --environment "$PHASE67_ENVIRONMENT" --mode "$PHASE67_MODE" || failed=1
  p67_run_check verify-bundle python3 scripts/phase67/phase67_control.py verify-bundle \
    --archive "$bundle_dir/phase67-bau-acceptance.tar.gz" \
    --checksum "$bundle_dir/phase67-bau-acceptance.tar.gz.sha256" \
    --output "$bundle_dir/archive-verification.json" || failed=1
  if [[ "$PHASE67_MODE" == execute ]]; then
    : "${PHASE67_SIGNING_KEY:?PHASE67_SIGNING_KEY is required in execute mode}"
    : "${PHASE67_SIGNING_PUBLIC_KEY:?PHASE67_SIGNING_PUBLIC_KEY is required in execute mode}"
    p67_run_check sign-bundle openssl dgst -sha256 -sign "$PHASE67_SIGNING_KEY" \
      -out "$bundle_dir/phase67-bau-acceptance.tar.gz.sig" "$bundle_dir/phase67-bau-acceptance.tar.gz" || failed=1
    p67_run_check verify-signature openssl dgst -sha256 -verify "$PHASE67_SIGNING_PUBLIC_KEY" \
      -signature "$bundle_dir/phase67-bau-acceptance.tar.gz.sig" "$bundle_dir/phase67-bau-acceptance.tar.gz" || failed=1
  fi
fi
if (( failed )); then p67_write_result FAIL; exit 1; fi
p67_write_result
