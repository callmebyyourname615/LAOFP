#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; cd "$ROOT"; source scripts/assurance/common.sh
assurance_require_identity; assurance_phase_begin 58G 'Liquidity, Collateral & Settlement Risk'; assurance_require_environment financial-control operations
: "${SETTLEMENT_RISK_SNAPSHOT:?SETTLEMENT_RISK_SNAPSHOT is required}"; assurance_require_file "${SETTLEMENT_RISK_SNAPSHOT}"
assurance_require_phase_pass 58F
failed=0
assurance_run_check input-identity python3 scripts/assurance/verify_input_identity.py --input "${SETTLEMENT_RISK_SNAPSHOT}" --release-reference "$RELEASE_REFERENCE" --git-commit "$RELEASE_GIT_COMMIT" --image-digest "$RELEASE_IMAGE_DIGEST" || failed=1
assurance_run_check domain-evaluation python3 scripts/assurance/evaluate_settlement_risk.py --snapshot "${SETTLEMENT_RISK_SNAPSHOT}" --thresholds "$ASSURANCE_THRESHOLDS" --now "${ASSURANCE_NOW:-$(assurance_now)}" --liquidity-policy settlement-risk/liquidity-risk-policy.yaml --collateral-policy settlement-risk/collateral-policy.yaml --exposure-policy settlement-risk/exposure-limit-policy.yaml --report-output "$PHASE_DIR/settlement-risk.json" --decision-output "$PHASE_DIR/liquidity-decision.json" || failed=1
assurance_write_result "$([[ $failed -eq 0 ]] && echo '' || echo FAIL)"
