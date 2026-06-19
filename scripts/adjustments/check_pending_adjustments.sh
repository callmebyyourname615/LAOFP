#!/usr/bin/env bash
set -euo pipefail
: "${DATABASE_URL:?DATABASE_URL is required}"
MAX_PENDING_HOURS="${MAX_PENDING_ADJUSTMENT_HOURS:-4}"
read -r count oldest <<<"$(psql "$DATABASE_URL" -AtX -v ON_ERROR_STOP=1 -v max_hours="$MAX_PENDING_HOURS" <<'SQL'
SELECT count(*),coalesce(extract(epoch FROM (now()-min(created_at)))::bigint,0)
FROM manual_financial_adjustment
WHERE status IN ('SUBMITTED','APPROVED') AND created_at<now()-(:'max_hours'||' hours')::interval;
SQL
)"
printf 'switching_adjustments_overdue %s\n' "$count"
printf 'switching_adjustments_oldest_age_seconds %s\n' "$oldest"
[[ "$count" == "0" ]] || { echo "overdue manual adjustments detected" >&2; exit 2; }
