#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; cd "$ROOT"; source scripts/assurance/common.sh
assurance_require_identity; assurance_phase_begin 58B 'Participant & Scheme Governance'; assurance_require_environment operations compliance
: "${PARTICIPANT_GOVERNANCE_SNAPSHOT:?PARTICIPANT_GOVERNANCE_SNAPSHOT is required}"; assurance_require_file "${PARTICIPANT_GOVERNANCE_SNAPSHOT}"
assurance_require_phase_pass 58A
failed=0
assurance_run_check input-identity python3 scripts/assurance/verify_input_identity.py --input "${PARTICIPANT_GOVERNANCE_SNAPSHOT}" --release-reference "$RELEASE_REFERENCE" --git-commit "$RELEASE_GIT_COMMIT" --image-digest "$RELEASE_IMAGE_DIGEST" || failed=1
assurance_run_check domain-evaluation python3 scripts/assurance/evaluate_participant_governance.py --snapshot "${PARTICIPANT_GOVERNANCE_SNAPSHOT}" --thresholds "$ASSURANCE_THRESHOLDS" --now "${ASSURANCE_NOW:-$(assurance_now)}" --policy participant-governance/certification-policy.yaml --risk-policy participant-governance/risk-tier-policy.yaml --report-output "$PHASE_DIR/participant-governance.json" --decision-output "$PHASE_DIR/certification-decision.json" || failed=1
assurance_write_result "$([[ $failed -eq 0 ]] && echo '' || echo FAIL)"
