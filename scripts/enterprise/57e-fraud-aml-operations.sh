#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; cd "$ROOT"; source scripts/enterprise/common.sh
enterprise_require_identity; enterprise_phase_begin 57E "Advanced Fraud and AML Operations"; enterprise_require_environment production security; enterprise_require_phase_pass 57A
: "${FRAUD_SIGNAL_SNAPSHOT:?FRAUD_SIGNAL_SNAPSHOT is required}"
enterprise_copy_input "$FRAUD_SIGNAL_SNAPSHOT" "$PHASE_DIR/fraud-signal-snapshot.json"
failed=0
enterprise_run_check input-release-identity python3 scripts/enterprise/verify_input_identity.py --release-reference "$RELEASE_REFERENCE" --git-commit "$RELEASE_GIT_COMMIT" --image-digest "$RELEASE_IMAGE_DIGEST" "$PHASE_DIR/fraud-signal-snapshot.json" || failed=1
enterprise_run_check fraud-and-aml-evaluation python3 scripts/enterprise/evaluate_fraud_signals.py --snapshot "$PHASE_DIR/fraud-signal-snapshot.json" --rules fraud/rule-catalog.yaml --scoring fraud/risk-scoring-policy.yaml --routing fraud/case-routing.yaml --thresholds "$ENTERPRISE_THRESHOLDS" --report-output "$PHASE_DIR/fraud-risk-report.json" --routing-output "$PHASE_DIR/case-routing-report.json" || failed=1
enterprise_write_result "$([[ $failed -eq 0 ]] && echo '' || echo FAIL)"
