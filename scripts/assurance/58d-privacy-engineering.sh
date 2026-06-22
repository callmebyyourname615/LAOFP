#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; cd "$ROOT"; source scripts/assurance/common.sh
assurance_require_identity; assurance_phase_begin 58D 'Privacy Engineering & Data Subject Rights'; assurance_require_environment compliance security
: "${PRIVACY_ASSURANCE_SNAPSHOT:?PRIVACY_ASSURANCE_SNAPSHOT is required}"; assurance_require_file "${PRIVACY_ASSURANCE_SNAPSHOT}"
assurance_require_phase_pass 58A
failed=0
assurance_run_check input-identity python3 scripts/assurance/verify_input_identity.py --input "${PRIVACY_ASSURANCE_SNAPSHOT}" --release-reference "$RELEASE_REFERENCE" --git-commit "$RELEASE_GIT_COMMIT" --image-digest "$RELEASE_IMAGE_DIGEST" || failed=1
assurance_run_check domain-evaluation python3 scripts/assurance/evaluate_privacy_engineering.py --snapshot "${PRIVACY_ASSURANCE_SNAPSHOT}" --thresholds "$ASSURANCE_THRESHOLDS" --now "${ASSURANCE_NOW:-$(assurance_now)}" --control-policy privacy-engineering/privacy-control-policy.yaml --rights-policy privacy-engineering/rights-case-policy.yaml --breach-policy privacy-engineering/breach-notification-policy.yaml --report-output "$PHASE_DIR/privacy-assurance.json" --decision-output "$PHASE_DIR/rights-case-decision.json" || failed=1
assurance_write_result "$([[ $failed -eq 0 ]] && echo '' || echo FAIL)"
