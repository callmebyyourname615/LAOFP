#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; cd "$ROOT"; source scripts/enterprise/common.sh
enterprise_require_identity; enterprise_phase_begin 57A "Continuous Production Re-Certification"; enterprise_require_environment production operations compliance
: "${PHASE57_CHANGESET_JSON:?PHASE57_CHANGESET_JSON is required}"
: "${PHASE57_CERTIFICATION_RESULTS_JSON:?PHASE57_CERTIFICATION_RESULTS_JSON is required}"
: "${PHASE57_PRIOR_CERTIFICATE_JSON:?PHASE57_PRIOR_CERTIFICATE_JSON is required}"
enterprise_copy_input "$PHASE57_CHANGESET_JSON" "$PHASE_DIR/changeset.json"
enterprise_copy_input "$PHASE57_CERTIFICATION_RESULTS_JSON" "$PHASE_DIR/certification-results.json"
enterprise_copy_input "$PHASE57_PRIOR_CERTIFICATE_JSON" "$PHASE_DIR/prior-certificate.json"
failed=0
enterprise_run_check impact-analysis python3 scripts/enterprise/detect_control_impact.py --changes "$PHASE_DIR/changeset.json" --rules certification/enterprise/impact-rules.yaml --full-recertification-on-unmapped --output "$PHASE_DIR/control-impact.json" || failed=1
enterprise_run_check recertification-decision python3 scripts/enterprise/evaluate_recertification.py --impact "$PHASE_DIR/control-impact.json" --results "$PHASE_DIR/certification-results.json" --prior-certificate "$PHASE_DIR/prior-certificate.json" --validity certification/enterprise/certification-validity.yaml --release-reference "$RELEASE_REFERENCE" --git-commit "$RELEASE_GIT_COMMIT" --image-digest "$RELEASE_IMAGE_DIGEST" --output "$PHASE_DIR/recertification-report.json" --validity-output "$PHASE_DIR/certification-validity.json" || failed=1
enterprise_write_result "$([[ $failed -eq 0 ]] && echo '' || echo FAIL)"
