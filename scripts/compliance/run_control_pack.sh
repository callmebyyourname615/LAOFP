#!/usr/bin/env bash
set -euo pipefail
: "${DB_URL:?DB_URL required}"
RUN_ID="control-$(date -u +%Y%m%dT%H%M%SZ)"
psql "$DB_URL" -v ON_ERROR_STOP=1 -Atc "select control_code || E'	' || evidence_query from compliance_control_definition where enabled=true" | while IFS=$'	' read -r control query; do
  failures=$(psql "$DB_URL" -v ON_ERROR_STOP=1 -Atc "$query")
  result=PASS; [ "${failures:-0}" = "0" ] || result=FAIL
  psql "$DB_URL" -v ON_ERROR_STOP=1 -c "insert into compliance_control_run(run_id, control_code, result, completed_at) values ('$RUN_ID','$control','$result',now())"
done
