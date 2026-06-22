#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; day2_require_identity; day2_phase_begin 56A "SLO and Error Budget Governance"; day2_require_environment production operations
snapshot="${SLO_METRICS_SNAPSHOT:-$PHASE_DIR/slo-metrics-snapshot.json}"
if [[ -n "${SLO_METRICS_SNAPSHOT:-}" ]]; then [[ -f "$snapshot" ]] || day2_die "metrics snapshot missing"; else : "${PROMETHEUS_URL:?PROMETHEUS_URL is required when SLO_METRICS_SNAPSHOT is absent}"; python3 scripts/day2/collect_slo_snapshot.py --prometheus-url "$PROMETHEUS_URL" --queries slo/prometheus-queries.yaml --output "$snapshot"; fi
day2_run_check calculate-budget python3 scripts/day2/calculate_error_budget.py --catalog slo/slo-catalog.yaml --metrics "$snapshot" --thresholds "$DAY2_THRESHOLDS" --output "$PHASE_DIR/slo-report.json" --decision-output "$PHASE_DIR/release-budget-decision.json" || true
day2_write_result
