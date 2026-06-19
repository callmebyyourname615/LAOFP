#!/usr/bin/env bash
set -euo pipefail
: "${DB_URL:?DB_URL required}"
overdue=$(psql "$DB_URL" -v ON_ERROR_STOP=1 -Atc "SELECT count(*) FROM corrective_action WHERE status NOT IN ('DONE','RISK_ACCEPTED') AND due_at<now()")
[[ "$overdue" == "0" ]] || { echo "overdue corrective actions: $overdue" >&2; exit 1; }
echo "corrective action due-date control PASS"
