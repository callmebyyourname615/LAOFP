#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; cd "$ROOT"; source scripts/assurance/common.sh
assurance_require_identity; assurance_phase_begin 58H 'Operational Digital Twin & Scenario Simulation'; assurance_require_environment simulation operations
: "${DIGITAL_TWIN_SNAPSHOT:?DIGITAL_TWIN_SNAPSHOT is required}"; assurance_require_file "${DIGITAL_TWIN_SNAPSHOT}"
assurance_require_phase_pass 58G
failed=0
assurance_run_check input-identity python3 scripts/assurance/verify_input_identity.py --input "${DIGITAL_TWIN_SNAPSHOT}" --release-reference "$RELEASE_REFERENCE" --git-commit "$RELEASE_GIT_COMMIT" --image-digest "$RELEASE_IMAGE_DIGEST" || failed=1
assurance_run_check domain-evaluation python3 scripts/assurance/evaluate_digital_twin.py --snapshot "${DIGITAL_TWIN_SNAPSHOT}" --thresholds "$ASSURANCE_THRESHOLDS" --now "${ASSURANCE_NOW:-$(assurance_now)}" --scenario-policy digital-twin/scenario-policy.yaml --data-policy digital-twin/data-policy.yaml --acceptance-policy digital-twin/acceptance-policy.yaml --report-output "$PHASE_DIR/digital-twin.json" --certificate-output "$PHASE_DIR/scenario-certificate.json" || failed=1
assurance_write_result "$([[ $failed -eq 0 ]] && echo '' || echo FAIL)"
