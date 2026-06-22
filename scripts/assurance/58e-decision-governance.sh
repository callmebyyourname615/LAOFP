#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; cd "$ROOT"; source scripts/assurance/common.sh
assurance_require_identity; assurance_phase_begin 58E 'Decision Model & Rule Governance'; assurance_require_environment security compliance
: "${DECISION_GOVERNANCE_SNAPSHOT:?DECISION_GOVERNANCE_SNAPSHOT is required}"; assurance_require_file "${DECISION_GOVERNANCE_SNAPSHOT}"
assurance_require_phase_pass 58D
failed=0
assurance_run_check input-identity python3 scripts/assurance/verify_input_identity.py --input "${DECISION_GOVERNANCE_SNAPSHOT}" --release-reference "$RELEASE_REFERENCE" --git-commit "$RELEASE_GIT_COMMIT" --image-digest "$RELEASE_IMAGE_DIGEST" || failed=1
assurance_run_check domain-evaluation python3 scripts/assurance/evaluate_decision_governance.py --snapshot "${DECISION_GOVERNANCE_SNAPSHOT}" --thresholds "$ASSURANCE_THRESHOLDS" --now "${ASSURANCE_NOW:-$(assurance_now)}" --model-policy decision-governance/model-rule-policy.yaml --change-policy decision-governance/change-policy.yaml --monitoring-policy decision-governance/monitoring-policy.yaml --report-output "$PHASE_DIR/decision-governance.json" --decision-output "$PHASE_DIR/rollout-decision.json" || failed=1
assurance_write_result "$([[ $failed -eq 0 ]] && echo '' || echo FAIL)"
