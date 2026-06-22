#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; cd "$ROOT"; source scripts/enterprise/common.sh
enterprise_require_identity; enterprise_phase_begin 57I "Business Continuity and Dependency Resilience"; enterprise_require_environment production operations dr; enterprise_require_phase_pass 57A 57B
: "${DEPENDENCY_RESILIENCE_SNAPSHOT:?required}"
enterprise_copy_input "$DEPENDENCY_RESILIENCE_SNAPSHOT" "$PHASE_DIR/dependency-resilience-snapshot.json"
failed=0
enterprise_run_check input-release-identity python3 scripts/enterprise/verify_input_identity.py --release-reference "$RELEASE_REFERENCE" --git-commit "$RELEASE_GIT_COMMIT" --image-digest "$RELEASE_IMAGE_DIGEST" "$PHASE_DIR/dependency-resilience-snapshot.json" || failed=1
enterprise_run_check dependency-resilience python3 scripts/enterprise/evaluate_dependency_resilience.py --snapshot "$PHASE_DIR/dependency-resilience-snapshot.json" --catalog continuity/dependency-catalog.yaml --degraded-policy continuity/degraded-mode-policy.yaml --scenarios continuity/scenario-catalog.yaml --thresholds "$ENTERPRISE_THRESHOLDS" --report-output "$PHASE_DIR/dependency-resilience-report.json" --decisions-output "$PHASE_DIR/degraded-mode-decisions.json" || failed=1
enterprise_write_result "$([[ $failed -eq 0 ]] && echo '' || echo FAIL)"
