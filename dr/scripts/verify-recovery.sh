#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"; require_dr_confirmation
: "${DB_URL:?}"; : "${DB_USERNAME:?}"; : "${DB_PASSWORD:?}"; : "${BASE_URL:?}"
export PGPASSWORD="$DB_PASSWORD"; record VERIFY START
curl --fail-with-body -sS "$BASE_URL/actuator/health" > "$EVIDENCE_DIR/health.json"
psql "$DB_URL" -U "$DB_USERNAME" -v ON_ERROR_STOP=1 -AtF, -c "SELECT 'transactions',count(*) FROM transactions UNION ALL SELECT 'outbox_messages',count(*) FROM outbox_messages UNION ALL SELECT 'audit_logs',count(*) FROM audit_logs" > "$EVIDENCE_DIR/post-counts.csv"
python3 "$(dirname "$0")/verify-counts.py" "$EVIDENCE_DIR/baseline-counts.csv" "$EVIDENCE_DIR/post-counts.csv" > "$EVIDENCE_DIR/reconciliation.json"
record VERIFY COMPLETE
