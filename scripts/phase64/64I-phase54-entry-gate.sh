#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE64_ROOT"
phase64_setup "64I" "Phase 54 entry gate"
STATUS=FAIL; MESSAGE="Phase 54 entry gate blocked"
trap 'code=$?; phase64_finalize "$STATUS" "$code" "$MESSAGE"' EXIT
phase64_require_file scripts/phase64/evaluate_entry_gate.py
phase64_require_file schemas/phase64-entry-decision.schema.json
if phase64_is_preflight; then
  phase64_run "validate Phase 64 static contract" python3 scripts/verify_phase64_static.py
  STATUS=PREPARED; MESSAGE="Phase 54 gate requires 64A-64H PASS under one release identity"; exit 0
fi
phase64_require_release_identity
phase61_manifest="$PHASE64_RUN_DIR/64B/phase61/manifest.json"
runtime_manifest="$PHASE64_RUN_DIR/64C/runtime/manifest.json"
phase64_require_file "$phase61_manifest"
phase64_require_file "$runtime_manifest"
phase64_run "reverify Phase 61 evidence at gate time" python3 scripts/phase61/verify_evidence_manifest.py \
  --manifest "$phase61_manifest" --schema schemas/phase61-evidence-manifest.schema.json
phase64_run "reverify runtime evidence at gate time" python3 scripts/evidence/verify_runtime_evidence.py "$runtime_manifest" \
  --require-go-live-ready --expected-commit "$RELEASE_GIT_COMMIT" \
  --expected-digest "$APPLICATION_IMAGE_DIGEST" --expected-reference "$RELEASE_REFERENCE"
phase64_run "evaluate Phase 54 entry decision" python3 scripts/phase64/evaluate_entry_gate.py \
  --config "$PHASE64_CONFIG" --run-dir "$PHASE64_RUN_DIR" \
  --reference "$RELEASE_REFERENCE" --commit "$RELEASE_GIT_COMMIT" \
  --application-digest "$APPLICATION_IMAGE_DIGEST" --migration-digest "$MIGRATION_IMAGE_DIGEST" \
  --phase61-manifest "$phase61_manifest" --runtime-manifest "$runtime_manifest" \
  --output "$PHASE64_PHASE_DIR/phase54-entry-decision.json"
STATUS=PASS; MESSAGE="APPROVE_PHASE54_ENTRY: all machine evidence gates passed"
