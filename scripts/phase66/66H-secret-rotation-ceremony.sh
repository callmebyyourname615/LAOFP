#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE66_ROOT"
phase66_setup "66H" "Secret-rotation ceremony control"
STATUS="FAIL"; MESSAGE="secret-rotation ceremony failed"
trap 'code=$?; phase66_finalize "$STATUS" "$code" "$MESSAGE"' EXIT
phase66_require_file docs/phase66/66H_SECRET_ROTATION_CEREMONY.md
phase66_require_file schemas/phase66/secret-rotation-attestation.schema.json
if ! phase66_is_full; then
  phase66_run "scan ceremony templates for secret-like assignments" python3 scripts/phase66/verify_secret_ceremony.py --root . --contract-only --output "$PHASE66_PHASE_DIR/secret-rotation.json"
  STATUS="PREPARED"; MESSAGE="rotation ceremony and redaction controls are ready; no credential changed"; exit 0
fi
phase66_require_uat; phase66_require_secret_ceremony_confirmation
: "${SECRET_ROTATION_ATTESTATION:?SECRET_ROTATION_ATTESTATION required}"
phase66_run "verify signed redacted attestation" python3 scripts/phase66/verify_secret_ceremony.py \
  --root . --attestation "$SECRET_ROTATION_ATTESTATION" --output "$PHASE66_PHASE_DIR/secret-rotation.json"
STATUS="PASS"; MESSAGE="signed rotation, purge, token/cache invalidation and re-clone attestations verified without secret values"
