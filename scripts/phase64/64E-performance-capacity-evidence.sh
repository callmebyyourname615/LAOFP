#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE64_ROOT"
phase64_setup "64E" "Performance and capacity evidence"
STATUS=FAIL; MESSAGE="performance evidence certification failed"
trap 'code=$?; phase64_finalize "$STATUS" "$code" "$MESSAGE"' EXIT
phase64_require_file scripts/phase64/validate_performance_evidence.py
phase64_require_file performance/scripts/run-k6.sh
if phase64_is_preflight; then
  phase64_run "validate performance configuration" python3 - "$PHASE64_CONFIG" <<'PY'
import pathlib,sys,yaml
cfg=yaml.safe_load(pathlib.Path(sys.argv[1]).read_text())
required={'smoke','sustained-2k-tps','sustained-10k-tps','burst-20k-tps','soak-8h'}
assert set(cfg['performance']['scenarios'])==required
print('Performance contract: PASS')
PY
  STATUS=PREPARED; MESSAGE="10K sustained, 20K burst and soak thresholds are configured"; exit 0
fi
phase64_require_release_identity
manifest="$PHASE64_RUN_DIR/64C/runtime/manifest.json"
phase64_require_file "$manifest"
phase64_run "certify base runtime performance controls" python3 scripts/phase64/extract_runtime_controls.py \
  --manifest "$manifest" --category base-performance-capacity \
  --required performance-smoke performance-sustained-2k capacity-snapshot settlement-500k soak-8h \
  --output "$PHASE64_PHASE_DIR/runtime-performance-controls.json"
evidence_dir="$PHASE64_PHASE_DIR/performance-evidence"
mkdir -p "$evidence_dir"
runtime_performance="$PHASE64_RUN_DIR/64C/runtime/artifacts/performance"
if [[ -d "$runtime_performance" ]]; then cp -a "$runtime_performance/." "$evidence_dir/"; fi
if [[ -n "${PHASE64_SUPPLEMENTAL_PERFORMANCE_DIR:-}" ]]; then
  phase64_require_dir "$PHASE64_SUPPLEMENTAL_PERFORMANCE_DIR"
  cp -a "$PHASE64_SUPPLEMENTAL_PERFORMANCE_DIR/." "$evidence_dir/"
fi
if [[ "${PHASE64_RUN_SUPPLEMENTAL_PERFORMANCE:-false}" == "true" ]]; then
  export RESULT_DIR="$evidence_dir"
  phase64_run "run sustained 10K TPS" performance/scripts/run-k6.sh sustained-10k-tps
  phase64_run "run burst 20K TPS" performance/scripts/run-k6.sh burst-20k-tps
fi
phase64_run "validate all Phase 64 performance thresholds" python3 scripts/phase64/validate_performance_evidence.py \
  --config "$PHASE64_CONFIG" --evidence-root "$evidence_dir" \
  --reference "$RELEASE_REFERENCE" --commit "$RELEASE_GIT_COMMIT" \
  --application-digest "$APPLICATION_IMAGE_DIGEST" --output "$PHASE64_PHASE_DIR/performance-summary.json"
STATUS=PASS; MESSAGE="smoke, 2K, 10K, 20K and soak evidence meet Phase 64 thresholds"
