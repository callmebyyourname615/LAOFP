#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE64_ROOT"
phase64_setup "64B" "Phase 61 evidence acquisition"
STATUS=FAIL; MESSAGE="Phase 61 evidence acquisition failed"
trap 'code=$?; phase64_finalize "$STATUS" "$code" "$MESSAGE"' EXIT
phase64_require_file schemas/phase61-evidence-manifest.schema.json
phase64_require_file scripts/phase61/verify_evidence_manifest.py
phase64_require_file scripts/phase61/run_phase61.sh
if phase64_is_preflight; then
  phase64_run "validate Phase 61 evidence tooling" python3 scripts/phase61/verify_phase61_evidence_tools.py
  STATUS=PREPARED; MESSAGE="verified import and explicit UAT execution paths are ready"; exit 0
fi
phase64_require_release_identity
mode="${PHASE64_PHASE61_MODE:-import}"
case "$mode" in
  import)
    : "${PHASE61_MANIFEST:?PHASE61_MANIFEST is required in import mode}"
    phase64_require_file "$PHASE61_MANIFEST"
    source_dir="$(cd "$(dirname "$PHASE61_MANIFEST")" && pwd)"
    ;;
  execute)
    export PHASE61_RUN_ID="${PHASE61_RUN_ID:-phase64-$PHASE64_RUN_ID}"
    export PHASE61_EVIDENCE_ROOT="${PHASE61_EVIDENCE_ROOT:-$PHASE64_PHASE_DIR/phase61-execution}"
    phase64_run "execute Phase 61 full UAT certification" scripts/phase61/run_phase61.sh --full
    source_dir="$PHASE61_EVIDENCE_ROOT/$PHASE61_RUN_ID"
    ;;
  *) phase64_log "ERROR PHASE64_PHASE61_MODE must be import or execute"; exit 64 ;;
esac
phase64_copy_tree "$source_dir" "$PHASE64_PHASE_DIR/phase61"
manifest="$PHASE64_PHASE_DIR/phase61/manifest.json"
phase64_require_file "$manifest"
phase64_run "verify copied Phase 61 evidence hashes" python3 scripts/phase61/verify_evidence_manifest.py \
  --manifest "$manifest" --schema schemas/phase61-evidence-manifest.schema.json
phase64_run "verify Phase 61 release identity" python3 scripts/phase64/verify_release_identity.py \
  --kind phase61 --manifest "$manifest" --commit "$RELEASE_GIT_COMMIT" \
  --application-digest "$APPLICATION_IMAGE_DIGEST" --migration-digest "$MIGRATION_IMAGE_DIGEST"
printf '%s\n' "$manifest" > "$PHASE64_PHASE_DIR/phase61-manifest.path"
STATUS=PASS; MESSAGE="Phase 61 evidence is hash-valid, copied and release-bound"
