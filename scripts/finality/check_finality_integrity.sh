#!/usr/bin/env bash
set -euo pipefail
: "${DATABASE_URL:?DATABASE_URL is required}"
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -AtX <<'SQL'
WITH problems AS (
  SELECT 'expired_claim' AS problem,count(*)::bigint AS total FROM payment_idempotency_record WHERE status='CLAIMED' AND expires_at<now()
  UNION ALL
  SELECT 'stale_duplicate_fingerprint',count(*) FROM payment_duplicate_fingerprint WHERE expires_at<now()-interval '1 day'
  UNION ALL
  SELECT 'approved_reversal_expired',count(*) FROM payment_reversal_request WHERE status='APPROVED' AND expires_at<now()
  UNION ALL
  SELECT 'final_without_timestamp',count(*) FROM payment_finality_record WHERE finality_status='FINAL' AND finalized_at IS NULL
)
SELECT problem,total FROM problems ORDER BY problem;
SQL
