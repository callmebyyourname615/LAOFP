#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE64_ROOT"
phase64_setup "64G" "DR and failure-recovery evidence"
STATUS=FAIL; MESSAGE="DR evidence certification failed"
trap 'code=$?; phase64_finalize "$STATUS" "$code" "$MESSAGE"' EXIT
phase64_require_file scripts/phase64/validate_dr_attestation.py
phase64_require_file docs/templates/PHASE64_DR_ATTESTATION.example.json
if phase64_is_preflight; then
  phase64_run "validate DR attestation template JSON" python3 -m json.tool docs/templates/PHASE64_DR_ATTESTATION.example.json
  STATUS=PREPARED; MESSAGE="six-scenario DR and integrity evidence contract is ready"; exit 0
fi
phase64_require_release_identity
: "${DR_ATTESTATION:?DR_ATTESTATION is required}"
phase64_require_file "$DR_ATTESTATION"
manifest="$PHASE64_RUN_DIR/64C/runtime/manifest.json"
phase64_run "certify runtime DR control" python3 scripts/phase64/extract_runtime_controls.py \
  --manifest "$manifest" --category disaster-recovery --required dr-suite \
  --output "$PHASE64_PHASE_DIR/runtime-dr-control.json"
cp "$DR_ATTESTATION" "$PHASE64_PHASE_DIR/dr-attestation.json"
phase64_run "validate DR scenarios and transaction integrity" python3 scripts/phase64/validate_dr_attestation.py \
  --config "$PHASE64_CONFIG" --attestation "$PHASE64_PHASE_DIR/dr-attestation.json" \
  --reference "$RELEASE_REFERENCE" --commit "$RELEASE_GIT_COMMIT" \
  --application-digest "$APPLICATION_IMAGE_DIGEST" --output "$PHASE64_PHASE_DIR/dr-summary.json"
STATUS=PASS; MESSAGE="all required DR scenarios, recovery objectives and integrity checks passed"
