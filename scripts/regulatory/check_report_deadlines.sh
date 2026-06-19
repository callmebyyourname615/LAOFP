#!/usr/bin/env bash
set -euo pipefail
: "${DB_URL:?DB_URL required}"
overdue=$(psql "$DB_URL" -v ON_ERROR_STOP=1 -Atc "SELECT count(*) FROM regulatory_report_run WHERE status NOT IN ('ACKNOWLEDGED','REJECTED') AND period_end<current_date")
[[ "$overdue" == "0" ]] || { echo "overdue regulatory report runs: $overdue" >&2; exit 1; }
echo "regulatory report deadline control PASS"
