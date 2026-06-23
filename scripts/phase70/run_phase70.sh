#!/usr/bin/env bash
set -uo pipefail

MODE="${1:-preflight}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RUN_ID="${PHASE70_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
EVIDENCE_DIR="${PHASE70_EVIDENCE_DIR:-${ROOT}/evidence/phase70/${RUN_ID}}"
mkdir -p "${EVIDENCE_DIR}"

case "${MODE}" in
  preflight|verify) ;;
  *) echo "usage: $0 [preflight|verify]" >&2; exit 64 ;;
esac

STATUS="PREPARED"
EXIT_CODE=0
FAILED_STEP=""
COMPLETED_STEPS=()

write_result() {
  local completed_json failed_json
  completed_json="$(printf '%s\n' "${COMPLETED_STEPS[@]:-}" | python3 -c 'import json,sys; print(json.dumps([line.rstrip("\n") for line in sys.stdin if line.rstrip("\n")]))')"
  failed_json="$(python3 -c 'import json,sys; print(json.dumps(sys.argv[1]))' "${FAILED_STEP}")"
  cat > "${EVIDENCE_DIR}/phase70-result.json" <<JSON
{
  "phase": "70A-70J",
  "mode": "${MODE}",
  "status": "${STATUS}",
  "runId": "${RUN_ID}",
  "generatedAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "evidenceDirectory": "${EVIDENCE_DIR}",
  "completedSteps": ${completed_json},
  "failedStep": ${failed_json}
}
JSON
  python3 -m json.tool "${EVIDENCE_DIR}/phase70-result.json" >/dev/null
}

run_step() {
  local step="$1" log_file="$2"
  shift 2
  echo "==> ${step}"
  set +e
  "$@" 2>&1 | tee "${EVIDENCE_DIR}/${log_file}"
  local command_rc=${PIPESTATUS[0]}
  set -e
  if [[ ${command_rc} -ne 0 ]]; then
    STATUS="FAIL"
    EXIT_CODE=${command_rc}
    FAILED_STEP="${step}"
    write_result
    echo "Phase 70 ${MODE}: FAIL at ${step}" >&2
    echo "Evidence: ${EVIDENCE_DIR}" >&2
    exit "${EXIT_CODE}"
  fi
  COMPLETED_STEPS+=("${step}")
}

set -e
run_step "static-contract-verification" "static-verification.log" \
  python3 "${ROOT}/scripts/phase70/verify_phase70_static.py" \
    --root "${ROOT}" \
    --json-output "${EVIDENCE_DIR}/static-result.json"
run_step "participant-policy-normalization" "participant-policy-validation.log" \
  python3 -m json.tool "${ROOT}/config/phase70-participant-traffic-policy.yaml"

if [[ "${MODE}" == "verify" ]]; then
  pushd "${ROOT}" >/dev/null
  run_step "maven-compile" "maven-compile.log" ./mvnw -B -DskipTests compile
  run_step "phase70-targeted-tests" "targeted-tests.log" ./mvnw -B \
    -Dtest='WebhookEncryptionConfigurationContextTest,PostgresTemporalBinderTest,ParticipantRateLimitPolicyServiceTest,ParticipantTokenBucketServiceTest,ParticipantIdentityResolverTest,RateLimitFilterTest,ConsistencyAwareReportingJdbcOperationsTest,PromotionFunderLedgerReconciliationServiceTest,PromotionBudgetConcurrencyIntegrationTest,PromotionFunderLedgerReconciliationIntegrationTest,OperationsGenerateRoutesForBankIntegrationTest' \
    test
  run_step "maven-full-verify" "maven-verify.log" ./mvnw -B verify
  run_step "execute-and-verify" "execute-and-verify.log" ./scripts/execute-and-verify/00-run-all.sh
  popd >/dev/null
  STATUS="PASS"
fi

write_result
printf 'Phase 70 %s: %s\nEvidence: %s\n' "${MODE}" "${STATUS}" "${EVIDENCE_DIR}"
exit "${EXIT_CODE}"
