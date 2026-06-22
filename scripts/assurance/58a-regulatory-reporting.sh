#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; cd "$ROOT"; source scripts/assurance/common.sh
assurance_require_identity; assurance_phase_begin 58A 'Regulatory Reporting & Submission Assurance'; assurance_require_environment regulatory compliance
: "${REGULATORY_SUBMISSION_SNAPSHOT:?REGULATORY_SUBMISSION_SNAPSHOT is required}"; assurance_require_file "${REGULATORY_SUBMISSION_SNAPSHOT}"
failed=0
assurance_run_check input-identity python3 scripts/assurance/verify_input_identity.py --input "${REGULATORY_SUBMISSION_SNAPSHOT}" --release-reference "$RELEASE_REFERENCE" --git-commit "$RELEASE_GIT_COMMIT" --image-digest "$RELEASE_IMAGE_DIGEST" || failed=1
assurance_run_check domain-evaluation python3 scripts/assurance/evaluate_regulatory_reporting.py --snapshot "${REGULATORY_SUBMISSION_SNAPSHOT}" --thresholds "$ASSURANCE_THRESHOLDS" --now "${ASSURANCE_NOW:-$(assurance_now)}" --catalog regulatory-assurance/report-catalog.yaml --policy regulatory-assurance/submission-policy.yaml --report-output "$PHASE_DIR/regulatory-report.json" --decision-output "$PHASE_DIR/submission-decision.json" || failed=1
assurance_write_result "$([[ $failed -eq 0 ]] && echo '' || echo FAIL)"
