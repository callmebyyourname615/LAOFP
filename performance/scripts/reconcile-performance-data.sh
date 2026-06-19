#!/usr/bin/env bash
set -Eeuo pipefail
: "${DB_URL:?DB_URL is required}"
: "${DB_USERNAME:?DB_USERNAME is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"
expected="${PERF_EXPECTED_TX_COUNT:-0}"
[[ "$expected" =~ ^[0-9]+$ ]] || { echo "PERF_EXPECTED_TX_COUNT must be an integer" >&2; exit 2; }
out="${1:-performance/results/reconciliation-$(date -u +%Y%m%dT%H%M%SZ).csv}"
mkdir -p "$(dirname "$out")"
export PGPASSWORD="$DB_PASSWORD"
psql "$DB_URL" -U "$DB_USERNAME" -v ON_ERROR_STOP=1 -v expected_count="$expected" --csv > "$out" <<'SQL'
SELECT 'performance_transactions' AS check_name,
       COUNT(*)::text AS observed,
       ('>= ' || :expected_count)::text AS expected,
       (COUNT(*) >= :expected_count)::text AS passed
FROM transactions WHERE transaction_ref LIKE 'PERF-%'
UNION ALL
SELECT 'duplicate_transaction_refs', COUNT(*)::text, '0', (COUNT(*) = 0)::text
FROM (
  SELECT transaction_ref FROM transactions
  WHERE transaction_ref LIKE 'PERF-%'
  GROUP BY transaction_ref HAVING COUNT(*) > 1
) duplicates
UNION ALL
SELECT 'failed_outbox_events', COUNT(*)::text, '0', (COUNT(*) = 0)::text
FROM outbox_messages
WHERE status = 'FAILED' AND created_at >= CURRENT_TIMESTAMP - INTERVAL '24 hours'
UNION ALL
SELECT 'audit_chain_missing_hash', COUNT(*)::text, '0', (COUNT(*) = 0)::text
FROM audit_logs
WHERE created_at >= CURRENT_TIMESTAMP - INTERVAL '24 hours'
  AND entry_hash IS NULL;
SQL
python3 - "$out" <<'PY'
import csv, json, pathlib, sys
path = pathlib.Path(sys.argv[1])
rows = list(csv.DictReader(path.open(encoding='utf-8')))
failed = [row for row in rows if str(row.get('passed','')).lower() != 'true']
report = {"passed": not failed, "checks": rows, "failed": failed}
report_path = path.with_suffix('.json')
report_path.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
print(json.dumps(report, indent=2))
raise SystemExit(1 if failed else 0)
PY
