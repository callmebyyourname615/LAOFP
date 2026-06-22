#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; day2_require_identity; day2_phase_begin 56I "FinOps and Resource Efficiency"; day2_require_environment production operations
: "${FINOPS_SNAPSHOT:?FINOPS_SNAPSHOT is required}"; cp "$FINOPS_SNAPSHOT" "$PHASE_DIR/finops-snapshot.json"
day2_run_check finops python3 scripts/day2/analyze_finops.py --snapshot "$PHASE_DIR/finops-snapshot.json" --budget finops/resource-budget.yaml --forecast-policy finops/storage-forecast.yaml --output "$PHASE_DIR/finops-report.json" --forecast-output "$PHASE_DIR/capacity-forecast.json" || true
day2_write_result
