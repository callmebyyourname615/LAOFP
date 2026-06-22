#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; cd "$ROOT"; source scripts/assurance/common.sh
assurance_require_identity; assurance_phase_begin 58I 'Third-Party & Concentration Risk'; assurance_require_environment compliance security
: "${THIRD_PARTY_RISK_SNAPSHOT:?THIRD_PARTY_RISK_SNAPSHOT is required}"; assurance_require_file "${THIRD_PARTY_RISK_SNAPSHOT}"
assurance_require_phase_pass 58C 58D
failed=0
assurance_run_check input-identity python3 scripts/assurance/verify_input_identity.py --input "${THIRD_PARTY_RISK_SNAPSHOT}" --release-reference "$RELEASE_REFERENCE" --git-commit "$RELEASE_GIT_COMMIT" --image-digest "$RELEASE_IMAGE_DIGEST" || failed=1
assurance_run_check domain-evaluation python3 scripts/assurance/evaluate_third_party_risk.py --snapshot "${THIRD_PARTY_RISK_SNAPSHOT}" --thresholds "$ASSURANCE_THRESHOLDS" --now "${ASSURANCE_NOW:-$(assurance_now)}" --vendor-policy third-party-risk/vendor-risk-policy.yaml --concentration-policy third-party-risk/concentration-policy.yaml --monitoring-policy third-party-risk/continuous-monitoring-policy.yaml --report-output "$PHASE_DIR/third-party-risk.json" --decision-output "$PHASE_DIR/concentration-decision.json" || failed=1
assurance_write_result "$([[ $failed -eq 0 ]] && echo '' || echo FAIL)"
