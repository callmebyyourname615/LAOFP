#!/usr/bin/env bash
set -euo pipefail
: "${DB_URL:?DB_URL required}"
: "${REGION_CODE:?REGION_CODE required}"
: "${PROBE_URL:?PROBE_URL required}"
start=$(date +%s%3N)
code=$(curl -sk --max-time 5 -o /dev/null -w '%{http_code}' "$PROBE_URL")
end=$(date +%s%3N)
status=FAIL; [ "$code" = "200" ] && status=PASS
latency=$((end-start))
psql "$DB_URL" -v ON_ERROR_STOP=1 -c "insert into region_readiness_probe(region_code, probe_type, status, latency_ms, replication_lag_seconds) values ('$REGION_CODE','http-health','$status',$latency,0)"
