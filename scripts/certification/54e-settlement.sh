#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cert_require_release_identity
require_phase_pass 54A 54B 54C 54H
[[ "$CERTIFICATION_ENVIRONMENT" == performance || "$CERTIFICATION_ENVIRONMENT" == uat ]] || cert_die "54E requires UAT or performance"
[[ "${SETTLEMENT_CERTIFICATION_CONFIRMATION:-}" == I_UNDERSTAND_THIS_SEEDS_500000_TRANSACTIONS ]] || cert_die "invalid SETTLEMENT_CERTIFICATION_CONFIRMATION"
phase_begin 54E "Settlement 500k Certification"
failed=0
export SETTLEMENT_TX_COUNT=500000 RESULT_DIR="$PHASE_DIR/benchmark" SETTLEMENT_SUMMARY_OUTPUT="$PHASE_DIR/settlement-summary.json"
run_check settlement-500k performance/settlement/run_settlement_benchmark.sh || failed=1
run_check settlement-thresholds python3 - "$PHASE_DIR/settlement-summary.json" config/phase54-thresholds.yaml <<'PY' || failed=1
import json,sys,yaml
summary=json.load(open(sys.argv[1],encoding='utf-8')); cfg=yaml.safe_load(open(sys.argv[2],encoding='utf-8'))['settlement']
assert summary['transactions']==cfg['requiredTransactionCount']
assert summary['durationSeconds']<=cfg['maximumDurationSeconds']
assert summary.get('balanceMismatchCount',1)<=cfg['maximumBalanceMismatchCount']
assert summary.get('passed') is True
PY
write_phase_result "$([[ $failed -eq 0 ]] && echo PASS || echo FAIL)"
