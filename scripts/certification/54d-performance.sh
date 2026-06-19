#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cert_require_release_identity
require_phase_pass 54A 54B 54C 54H
[[ "$CERTIFICATION_ENVIRONMENT" == performance || "$CERTIFICATION_ENVIRONMENT" == uat ]] || cert_die "54D requires UAT or performance"
[[ "${PERFORMANCE_CERTIFICATION_CONFIRMATION:-}" == I_UNDERSTAND_THIS_GENERATES_SUSTAINED_LOAD ]] || cert_die "invalid PERFORMANCE_CERTIFICATION_CONFIRMATION"
: "${BASE_URL:?BASE_URL is required}"
: "${API_KEY:?API_KEY is required}"
: "${PROMETHEUS_URL:?PROMETHEUS_URL is required for capacity certification}"
phase_begin 54D "Performance & Capacity Certification"
failed=0
K6_RESULT_DIR="$PHASE_DIR/k6"
mkdir -p "$K6_RESULT_DIR"
run_check capacity-before env RESULT_DIR="$PHASE_DIR/capacity-before" performance/scripts/capture-capacity-evidence.sh || failed=1
for scenario in smoke sustained-2k-tps burst-10k-tps soak-8h; do
  run_check "$scenario" env RESULT_DIR="$K6_RESULT_DIR" performance/scripts/run-k6.sh "$scenario" || failed=1
done
run_check capacity-after env RESULT_DIR="$PHASE_DIR/capacity-after" performance/scripts/capture-capacity-evidence.sh || failed=1
run_check performance-thresholds python3 scripts/certification/summarize_performance.py \
  --results "$K6_RESULT_DIR" \
  --capacity-before "$PHASE_DIR/capacity-before/prometheus-snapshots.jsonl" \
  --capacity-after "$PHASE_DIR/capacity-after/prometheus-snapshots.jsonl" \
  --output "$PHASE_DIR/performance-summary.json" || failed=1
write_phase_result "$([[ $failed -eq 0 ]] && echo PASS || echo FAIL)"
