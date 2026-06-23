#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE61_ROOT"
phase_setup "61J" "UAT evidence bundle and release-candidate gate"
PHASE_STATUS="FAIL"; PHASE_MESSAGE="UAT evidence/RC gate failed"
trap 'code=$?; phase_finalize "$PHASE_STATUS" "$code" "$PHASE_MESSAGE"' EXIT
phase_require_file schemas/phase61-evidence-manifest.schema.json
if phase_is_preflight; then
  phase_run "Phase 61 evidence tooling contract" python3 scripts/phase61/verify_phase61_evidence_tools.py
  PHASE_STATUS="PREPARED"; PHASE_MESSAGE="immutable evidence bundle, approvals and RC gate are ready"; exit 0
fi
phase_require_uat_confirmation
: "${APPLICATION_IMAGE_DIGEST:?APPLICATION_IMAGE_DIGEST is required}"
: "${MIGRATION_IMAGE_DIGEST:?MIGRATION_IMAGE_DIGEST is required}"
: "${UAT_ENTRY_ATTESTATION:?UAT_ENTRY_ATTESTATION is required}"
phase_run "build immutable evidence manifest" python3 scripts/phase61/build_evidence_manifest.py \
  --run-dir "$PHASE61_RUN_DIR" --commit "$(git rev-parse HEAD)" \
  --application-image-digest "$APPLICATION_IMAGE_DIGEST" --migration-image-digest "$MIGRATION_IMAGE_DIGEST" \
  --approval "$UAT_ENTRY_ATTESTATION" --output "$PHASE61_RUN_DIR/manifest.json"
phase_run "verify immutable evidence manifest" python3 scripts/phase61/verify_evidence_manifest.py \
  --manifest "$PHASE61_RUN_DIR/manifest.json" --schema schemas/phase61-evidence-manifest.schema.json
PHASE_STATUS="PASS"; PHASE_MESSAGE="61A-61I evidence is immutable, digest-bound, approved and eligible for Phase 54 UAT certification"
