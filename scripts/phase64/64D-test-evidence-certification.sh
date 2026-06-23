#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE64_ROOT"
phase64_setup "64D" "Build, test, migration and sanctions evidence"
STATUS=FAIL; MESSAGE="test evidence certification failed"
trap 'code=$?; phase64_finalize "$STATUS" "$code" "$MESSAGE"' EXIT
phase64_require_file scripts/phase64/extract_runtime_controls.py
if phase64_is_preflight; then
  phase64_run "validate test control contract" python3 scripts/phase64/extract_runtime_controls.py --help
  STATUS=PREPARED; MESSAGE="test evidence collector requires static, Maven, migration and sanctions PASS controls"; exit 0
fi
phase64_require_release_identity
manifest="$PHASE64_RUN_DIR/64C/runtime/manifest.json"
phase64_require_file "$manifest"
phase64_run "certify repository and test controls" python3 scripts/phase64/extract_runtime_controls.py \
  --manifest "$manifest" --category build-test-migration-aml \
  --required static-gates full-maven-verify migration-v83-runtime sanctions-mock-sync \
  --output "$PHASE64_PHASE_DIR/test-evidence-summary.json"
reports="$PHASE64_RUN_DIR/64C/runtime/artifacts/surefire-reports.tar.gz"
if [[ -f "$reports" ]]; then cp "$reports" "$PHASE64_PHASE_DIR/surefire-reports.tar.gz"; fi
STATUS=PASS; MESSAGE="static gates, Maven verify, migration runtime and sanctions tests are PASS"
