#!/usr/bin/env bash
set -Eeuo pipefail
: "${BASE_URL:?BASE_URL is required}"
: "${API_KEY:?API_KEY is required}"
: "${DB_URL:?DB_URL is required}"
: "${DB_USERNAME:?DB_USERNAME is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"
COUNT="${SETTLEMENT_TX_COUNT:-500000}"
[[ "$COUNT" =~ ^[0-9]+$ ]] || { echo 'SETTLEMENT_TX_COUNT must be an integer' >&2; exit 2; }
OUT="${RESULT_DIR:-performance/results}/settlement-$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$OUT"
export PGPASSWORD="$DB_PASSWORD"

readarray -t dates < <(python3 - <<'PYDATES'
import datetime
business = datetime.date.today()
while business.weekday() >= 5:
    business -= datetime.timedelta(days=1)
settlement = business + datetime.timedelta(days=1)
while settlement.weekday() >= 5:
    settlement += datetime.timedelta(days=1)
print(business.isoformat())
print(settlement.isoformat())
PYDATES
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
# A second call is deliberate. The item/position totals must remain unchanged.
curl --fail-with-body -sS -X POST "$BASE_URL/api/operations/settlement/cycles/$cycle_ref/batch" \
  -H "X-API-Key: $API_KEY" | tee "$OUT/rebatch.json"
curl --fail-with-body -sS -X POST "$BASE_URL/api/operations/settlement/cycles/$cycle_ref/close" \
  -H "X-API-Key: $API_KEY" | tee "$OUT/close.json"
end=$(date +%s)

integrity_json="$(psql "$DB_URL" -U "$DB_USERNAME" -AtX -v ON_ERROR_STOP=1 \
  -v cycle_ref="$cycle_ref" -v business_date="$BUSINESS_DATE" -v expected_count="$COUNT" <<'SQL'
WITH cycle AS (
  SELECT id FROM settlement_cycles WHERE cycle_ref = :'cycle_ref'
), eligible AS (
  SELECT transaction_ref
  FROM transactions
  WHERE status = 'SETTLED'
    AND business_date = :'business_date'::date
    AND settlement_method = 'DNS'
    AND transaction_ref LIKE 'PERF-%'
), per_transfer AS (
  SELECT e.transaction_ref,
         count(si.*) FILTER (WHERE si.direction = 'DEBIT') AS debit_rows,
         count(si.*) FILTER (WHERE si.direction = 'CREDIT') AS credit_rows
  FROM eligible e
  LEFT JOIN settlement_items si
    ON si.cycle_id = (SELECT id FROM cycle)
   AND si.transaction_ref = e.transaction_ref
  GROUP BY e.transaction_ref
), duplicate_legs AS (
  SELECT count(*) AS total
  FROM (
    SELECT transaction_ref, bank_code, direction, settlement_date
    FROM settlement_items
    WHERE cycle_id = (SELECT id FROM cycle)
    GROUP BY transaction_ref, bank_code, direction, settlement_date
    HAVING count(*) > 1
  ) duplicates
), counter_violations AS (
  SELECT
      (SELECT count(*) FROM reporting.current_transaction_status WHERE total_count < 0)
    + (SELECT count(*) FROM reporting.current_inquiry_status WHERE total_count < 0)
    + (SELECT count(*) FROM reporting.current_outbox_status WHERE total_count < 0) AS total
), perf_outbox AS (
  SELECT count(*) AS total FROM outbox_messages
  WHERE transaction_ref LIKE 'PERF-%' AND status IN ('FAILED','PENDING','PROCESSING')
), position_totals AS (
  SELECT COALESCE(abs(sum(net_position)), 0) AS mismatch,
         COALESCE(sum(transaction_count), 0) AS transaction_legs
  FROM settlement_positions WHERE cycle_id = (SELECT id FROM cycle)
)
SELECT json_build_object(
  'eligibleTransactionCount', (SELECT count(*) FROM eligible),
  'settlementItemCount', (SELECT count(*) FROM settlement_items WHERE cycle_id = (SELECT id FROM cycle)),
  'missingTransactionCount', (SELECT count(*) FROM per_transfer WHERE debit_rows <> 1 OR credit_rows <> 1),
  'duplicatePostingCount', (SELECT total FROM duplicate_legs),
  'negativeCounterCount', (SELECT total FROM counter_violations),
  'outboxUndeliveredCount', (SELECT total FROM perf_outbox),
  'balanceMismatchAmount', (SELECT mismatch FROM position_totals),
  'positionTransactionLegCount', (SELECT transaction_legs FROM position_totals),
  'expectedTransactionCount', :expected_count::bigint
)::text;
SQL
)"

summary_path="$OUT/summary.json"
python3 - "$cycle_ref" "$COUNT" "$((end-start))" "$BUSINESS_DATE" "$SETTLEMENT_DATE" "$integrity_json" > "$summary_path" <<'PYSUMMARY'
import json, sys
cycle_ref, count, duration, business_date, settlement_date, integrity_raw = sys.argv[1:]
integrity = json.loads(integrity_raw)
count = int(count)
duration = int(duration)
expected_items = count * 2
zero_balance = str(integrity["balanceMismatchAmount"]) in {"0", "0.0", "0.00"}
passed = (
    duration <= 1800
    and integrity["eligibleTransactionCount"] == count
    and integrity["settlementItemCount"] == expected_items
    and integrity["positionTransactionLegCount"] == expected_items
    and integrity["missingTransactionCount"] == 0
    and integrity["duplicatePostingCount"] == 0
    and integrity["negativeCounterCount"] == 0
    and integrity["outboxUndeliveredCount"] == 0
    and zero_balance
)
print(json.dumps({
    "cycleRef": cycle_ref,
    "transactions": count,
    "durationSeconds": duration,
    "businessDate": business_date,
    "settlementDate": settlement_date,
    "targetSeconds": 1800,
    **integrity,
    "balanceMismatchCount": 0 if zero_balance else 1,
    "passed": passed,
}, indent=2, sort_keys=True))
PYSUMMARY

if [[ -n "${SETTLEMENT_SUMMARY_OUTPUT:-}" ]]; then
  mkdir -p "$(dirname "$SETTLEMENT_SUMMARY_OUTPUT")"
  cp "$summary_path" "$SETTLEMENT_SUMMARY_OUTPUT"
fi
python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); raise SystemExit(0 if d["passed"] else 1)' "$summary_path"
