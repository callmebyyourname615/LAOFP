#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; cd "$ROOT"; source scripts/enterprise/common.sh
enterprise_require_identity; enterprise_phase_begin 57G "Platform Engineering and Self-Service Operations"; enterprise_require_environment production operations; enterprise_require_phase_pass 57A 57F
: "${OPERATION_REQUEST_JSON:?OPERATION_REQUEST_JSON is required}"
enterprise_copy_input "$OPERATION_REQUEST_JSON" "$PHASE_DIR/operation-request.json"
failed=0
enterprise_run_check input-release-identity python3 scripts/enterprise/verify_input_identity.py --release-reference "$RELEASE_REFERENCE" --git-commit "$RELEASE_GIT_COMMIT" --image-digest "$RELEASE_IMAGE_DIGEST" "$PHASE_DIR/operation-request.json" || failed=1
enterprise_run_check operation-safety-validation python3 scripts/enterprise/validate_operation_request.py --request "$PHASE_DIR/operation-request.json" --catalog operations/operation-catalog.yaml --authorization operations/authorization-policy.yaml --approval-policy operations/approval-policy.yaml --safety-limits operations/safety-limits.yaml --validation-output "$PHASE_DIR/operation-validation.json" --plan-output "$PHASE_DIR/operation-plan.json" || failed=1
enterprise_write_result "$([[ $failed -eq 0 ]] && echo '' || echo FAIL)"
