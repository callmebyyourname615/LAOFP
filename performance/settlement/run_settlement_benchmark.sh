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

python3 - "$cycle_ref" "$COUNT" "$((end-start))" "$BUSINESS_DATE" "$SETTLEMENT_DATE" > "$OUT/summary.json" <<'PY'
import json, sys
cycle_ref, count, duration, business_date, settlement_date = sys.argv[1:]
print(json.dumps({
    "cycleRef": cycle_ref,
    "transactions": int(count),
    "durationSeconds": int(duration),
    "businessDate": business_date,
    "settlementDate": settlement_date,
    "targetSeconds": 1800,
    "passed": int(duration) < 1800,
}, indent=2))
PY
python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); raise SystemExit(0 if d["passed"] else 1)' "$OUT/summary.json"
