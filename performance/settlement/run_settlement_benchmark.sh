#!/usr/bin/env bash
set -Eeuo pipefail
: "${BASE_URL:?BASE_URL is required}"
: "${API_KEY:?API_KEY is required}"
: "${DB_URL:?DB_URL is required}"
: "${DB_USERNAME:?DB_USERNAME is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"
COUNT="${SETTLEMENT_TX_COUNT:-500000}"
OUT="${RESULT_DIR:-performance/results}/settlement-$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$OUT"
export PGPASSWORD="$DB_PASSWORD"

readarray -t dates < <(python3 - <<'PY'
import datetime
business = datetime.date.today()
while business.weekday() >= 5:
    business -= datetime.timedelta(days=1)
settlement = business + datetime.timedelta(days=1)
while settlement.weekday() >= 5:
    settlement += datetime.timedelta(days=1)
print(business.isoformat())
print(settlement.isoformat())
PY
)
BUSINESS_DATE="${PERF_BUSINESS_DATE:-${dates[0]}}"
SETTLEMENT_DATE="${PERF_SETTLEMENT_DATE:-${dates[1]}}"

psql "$DB_URL" -U "$DB_USERNAME" -v ON_ERROR_STOP=1 \
  -v SETTLEMENT_TX_COUNT="$COUNT" -v PERF_BUSINESS_DATE="$BUSINESS_DATE" \
  -f performance/settlement/generate_settlement_dataset.sql | tee "$OUT/seed.log"

start=$(date +%s)
cycle=$(curl --fail-with-body -sS -X POST "$BASE_URL/api/operations/settlement/cycles" \
  -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
  -d "{\"settlementDate\":\"$SETTLEMENT_DATE\",\"currency\":\"LAK\"}" | tee "$OUT/cycle.json")
cycle_ref=$(python3 -c 'import json,sys; print(json.load(sys.stdin).get("cycleRef", ""))' <<<"$cycle")
[[ -n "$cycle_ref" ]] || { echo 'cycleRef missing' >&2; exit 1; }

curl --fail-with-body -sS -X POST "$BASE_URL/api/operations/settlement/cycles/$cycle_ref/batch" \
  -H "X-API-Key: $API_KEY" | tee "$OUT/batch.json"
curl --fail-with-body -sS -X POST "$BASE_URL/api/operations/settlement/cycles/$cycle_ref/close" \
  -H "X-API-Key: $API_KEY" | tee "$OUT/close.json"
end=$(date +%s)

balance_mismatch_amount="$(psql "$DB_URL" -U "$DB_USERNAME" -AtX -v ON_ERROR_STOP=1 -v cycle_ref="$cycle_ref" -c "SELECT COALESCE(ABS(SUM(p.net_position)),0)::text FROM settlement_positions p JOIN settlement_cycles c ON c.id=p.cycle_id WHERE c.cycle_ref=:'cycle_ref'")"
balance_mismatch_count="$(python3 -c 'from decimal import Decimal; import sys; print(0 if Decimal(sys.argv[1]) == 0 else 1)' "$balance_mismatch_amount")"
summary_path="$OUT/summary.json"
python3 - "$cycle_ref" "$COUNT" "$((end-start))" "$BUSINESS_DATE" "$SETTLEMENT_DATE" "$balance_mismatch_amount" "$balance_mismatch_count" > "$summary_path" <<'PY'
import json, sys
cycle_ref, count, duration, business_date, settlement_date, mismatch_amount, mismatch_count = sys.argv[1:]
print(json.dumps({
    "cycleRef": cycle_ref,
    "transactions": int(count),
    "durationSeconds": int(duration),
    "businessDate": business_date,
    "settlementDate": settlement_date,
    "targetSeconds": 1800,
    "balanceMismatchAmount": mismatch_amount,
    "balanceMismatchCount": int(mismatch_count),
    "passed": int(duration) <= 1800 and int(mismatch_count) == 0,
}, indent=2))
PY
if [[ -n "${SETTLEMENT_SUMMARY_OUTPUT:-}" ]]; then mkdir -p "$(dirname "$SETTLEMENT_SUMMARY_OUTPUT")"; cp "$summary_path" "$SETTLEMENT_SUMMARY_OUTPUT"; fi
python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); raise SystemExit(0 if d["passed"] else 1)' "$summary_path"
