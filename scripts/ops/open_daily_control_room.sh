#!/usr/bin/env bash
set -euo pipefail
: "${DB_URL:?DB_URL required}"
: "${BUSINESS_DATE:?BUSINESS_DATE required}"
: "${OPENED_BY:?OPENED_BY required}"
psql "$DB_URL" -v ON_ERROR_STOP=1 -c "insert into ops_daily_control_room(business_date, opened_by) values ('${BUSINESS_DATE}', '${OPENED_BY}') on conflict (business_date) do nothing"
