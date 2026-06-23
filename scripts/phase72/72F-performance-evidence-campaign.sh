#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "$0")" && pwd)/common.sh"
phase=72F
for f in performance/scripts/run-k6.sh performance/scenarios/smoke.js performance/scenarios/sustained-2k-tps.js performance/scenarios/sustained-10k-tps.js performance/scenarios/burst-20k-tps.js performance/scenarios/soak-8h.js performance/settlement/run_settlement_benchmark.sh; do
  [[ -f "$PHASE72_ROOT/$f" ]] || { phase72_result "$phase" BLOCKED "Missing performance asset: $f"; exit 2; }
done
if [[ "$PHASE72_MODE" != full ]]; then phase72_result "$phase" PREPARED "Performance campaign assets are ready; load is not executed in preflight"; exit 0; fi
phase72_require_full "$phase" PHASE72_CONFIRM_LOAD
[[ -n "${BASE_URL:-}" && -n "${API_KEY:-}" ]] || { phase72_result "$phase" BLOCKED "BASE_URL and API_KEY are required"; exit 2; }
docker info >/dev/null 2>&1 || { phase72_result "$phase" BLOCKED "Docker is required for k6"; exit 2; }
result_dir="$PHASE72_ARTIFACT_DIR/performance"; mkdir -p "$result_dir"
cd "$PHASE72_ROOT"
for scenario in smoke sustained-2k-tps sustained-10k-tps burst-20k-tps soak-8h; do
  if ! phase72_run_logged "72F-$scenario" env RESULT_DIR="$result_dir" ./performance/scripts/run-k6.sh "$scenario"; then
    phase72_result "$phase" FAIL "Performance scenario failed: $scenario"; exit 1
  fi
done
for v in DB_URL DB_USERNAME DB_PASSWORD; do [[ -n "${!v:-}" ]] || { phase72_result "$phase" BLOCKED "$v is required for settlement 500K"; exit 2; }; done
if ! phase72_run_logged 72F-settlement env RESULT_DIR="$result_dir" SETTLEMENT_TX_COUNT=500000 ./performance/settlement/run_settlement_benchmark.sh; then
  phase72_result "$phase" FAIL "Settlement 500K benchmark failed"; exit 1
fi
summary="$PHASE72_ARTIFACT_DIR/performance-summary.json"
if python3 scripts/phase72/normalize_performance_results.py --results-root "$result_dir" --output "$summary" | tee "$PHASE72_LOG_DIR/72F-normalize.log"; then
  phase72_result "$phase" PASS "All performance scenarios and settlement 500K passed strict thresholds"
else phase72_result "$phase" FAIL "Performance evidence did not satisfy strict thresholds"; exit 1; fi
