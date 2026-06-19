#!/usr/bin/env bash
set -euo pipefail
: "${DB_URL:?DB_URL required}"
RUN_ID="dq-$(date -u +%Y%m%dT%H%M%SZ)"
psql "$DB_URL" -v ON_ERROR_STOP=1 -Atc "select rule_code || E'	' || sql_check from data_quality_rule where enabled=true" | while IFS=$'	' read -r rule sql; do
  count=$(psql "$DB_URL" -v ON_ERROR_STOP=1 -Atc "$sql")
  status=PASS; [ "${count:-0}" = "0" ] || status=FAIL
  psql "$DB_URL" -v ON_ERROR_STOP=1 -c "insert into data_quality_run(run_id, rule_code, status, failing_count, completed_at) values ('$RUN_ID','$rule','$status',$count,now())"
done
