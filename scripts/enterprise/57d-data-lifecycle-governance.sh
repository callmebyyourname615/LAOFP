#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; cd "$ROOT"; source scripts/enterprise/common.sh
enterprise_require_identity; enterprise_phase_begin 57D "Data Lifecycle and Retention Governance"; enterprise_require_environment production compliance; enterprise_require_phase_pass 57A
: "${DATA_LIFECYCLE_INVENTORY:?required}"; : "${DATA_DELETION_REQUEST:?required}"
enterprise_copy_input "$DATA_LIFECYCLE_INVENTORY" "$PHASE_DIR/data-inventory.json"; enterprise_copy_input "$DATA_DELETION_REQUEST" "$PHASE_DIR/deletion-request.json"
failed=0
enterprise_run_check input-release-identity python3 scripts/enterprise/verify_input_identity.py --release-reference "$RELEASE_REFERENCE" --git-commit "$RELEASE_GIT_COMMIT" --image-digest "$RELEASE_IMAGE_DIGEST" "$PHASE_DIR/data-inventory.json" "$PHASE_DIR/deletion-request.json" || failed=1
enterprise_run_check lifecycle-and-deletion-eligibility python3 scripts/enterprise/evaluate_data_lifecycle.py --inventory "$PHASE_DIR/data-inventory.json" --deletion-request "$PHASE_DIR/deletion-request.json" --retention-policy data-governance/retention-policy.yaml --deletion-policy data-governance/deletion-policy.yaml --thresholds "$ENTERPRISE_THRESHOLDS" --report-output "$PHASE_DIR/data-lifecycle-report.json" --eligibility-output "$PHASE_DIR/deletion-eligibility-report.json" || failed=1
enterprise_write_result "$([[ $failed -eq 0 ]] && echo '' || echo FAIL)"
