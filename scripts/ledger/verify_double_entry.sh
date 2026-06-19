#!/usr/bin/env bash
set -euo pipefail
: "${DB_URL:?DB_URL required}"
unbalanced=$(psql "$DB_URL" -v ON_ERROR_STOP=1 -Atc "SELECT count(*) FROM (SELECT j.id FROM control_journal j LEFT JOIN control_journal_entry e ON e.journal_id=j.id WHERE j.status='POSTED' GROUP BY j.id HAVING coalesce(sum(e.amount) FILTER (WHERE e.side='DEBIT'),0) <> coalesce(sum(e.amount) FILTER (WHERE e.side='CREDIT'),0)) x")
[[ "$unbalanced" == "0" ]] || { echo "unbalanced posted journals: $unbalanced" >&2; exit 1; }
echo "double-entry ledger control PASS"
