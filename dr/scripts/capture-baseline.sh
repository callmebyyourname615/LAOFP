#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"
require_dr_confirmation
: "${DB_URL:?}"
: "${DB_USERNAME:?}"
: "${DB_PASSWORD:?}"
export PGPASSWORD="$DB_PASSWORD"
record BASELINE START
psql "$DB_URL" -U "$DB_USERNAME" -v ON_ERROR_STOP=1 -AtF, -c "SELECT 'transactions',count(*) FROM transactions UNION ALL SELECT 'outbox_messages',count(*) FROM outbox_messages UNION ALL SELECT 'audit_logs',count(*) FROM audit_logs" > "$EVIDENCE_DIR/baseline-counts.csv"
psql "$DB_URL" -U "$DB_USERNAME" -v ON_ERROR_STOP=1 -AtF, -c "SELECT 'duplicate_transaction_refs',COALESCE(SUM(c-1),0) FROM (SELECT count(*) c FROM transactions GROUP BY transaction_ref,business_date HAVING count(*)>1) d" > "$EVIDENCE_DIR/baseline-integrity.csv"
kubectl -n "$DR_NAMESPACE" get pods,deploy,hpa -o wide > "$EVIDENCE_DIR/baseline-k8s.txt"
kubectl -n "$DR_NAMESPACE" get events --sort-by=.lastTimestamp > "$EVIDENCE_DIR/baseline-events.txt"
record BASELINE COMPLETE
