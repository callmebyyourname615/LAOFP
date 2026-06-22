#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; cd "$ROOT"; source scripts/enterprise/common.sh
enterprise_require_identity; enterprise_phase_begin 57C "Ledger Integrity and Financial Control"; enterprise_require_environment production financial-control; enterprise_require_phase_pass 57A
: "${FINANCIAL_CONTROL_SNAPSHOT:?FINANCIAL_CONTROL_SNAPSHOT is required}"
enterprise_copy_input "$FINANCIAL_CONTROL_SNAPSHOT" "$PHASE_DIR/financial-snapshot.json"
failed=0
enterprise_run_check input-release-identity python3 scripts/enterprise/verify_input_identity.py --release-reference "$RELEASE_REFERENCE" --git-commit "$RELEASE_GIT_COMMIT" --image-digest "$RELEASE_IMAGE_DIGEST" "$PHASE_DIR/financial-snapshot.json" || failed=1
enterprise_run_check financial-integrity python3 scripts/enterprise/evaluate_financial_controls.py --snapshot "$PHASE_DIR/financial-snapshot.json" --catalog financial-controls/control-catalog.yaml --thresholds "$ENTERPRISE_THRESHOLDS" --report-output "$PHASE_DIR/financial-control-report.json" --decision-output "$PHASE_DIR/financial-freeze-decision.json" || failed=1
enterprise_write_result "$([[ $failed -eq 0 ]] && echo '' || echo FAIL)"
