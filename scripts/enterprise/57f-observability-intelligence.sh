#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; cd "$ROOT"; source scripts/enterprise/common.sh
enterprise_require_identity; enterprise_phase_begin 57F "Observability Intelligence and Anomaly Detection"; enterprise_require_environment production operations; enterprise_require_phase_pass 57A
: "${OBSERVABILITY_INTELLIGENCE_SNAPSHOT:?required}"
enterprise_copy_input "$OBSERVABILITY_INTELLIGENCE_SNAPSHOT" "$PHASE_DIR/observability-snapshot.json"
failed=0
enterprise_run_check input-release-identity python3 scripts/enterprise/verify_input_identity.py --release-reference "$RELEASE_REFERENCE" --git-commit "$RELEASE_GIT_COMMIT" --image-digest "$RELEASE_IMAGE_DIGEST" "$PHASE_DIR/observability-snapshot.json" || failed=1
enterprise_run_check anomaly-and-correlation python3 scripts/enterprise/detect_observability_anomalies.py --snapshot "$PHASE_DIR/observability-snapshot.json" --policy observability/intelligence/anomaly-policy.yaml --seasonal-baselines observability/intelligence/seasonal-baselines.yaml --correlation-rules observability/intelligence/correlation-rules.yaml --suppression-policy observability/intelligence/suppression-policy.yaml --thresholds "$ENTERPRISE_THRESHOLDS" --anomaly-output "$PHASE_DIR/anomaly-report.json" --correlation-output "$PHASE_DIR/correlation-report.json" || failed=1
enterprise_write_result "$([[ $failed -eq 0 ]] && echo '' || echo FAIL)"
