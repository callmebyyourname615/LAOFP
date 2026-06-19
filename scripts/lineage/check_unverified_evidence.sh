#!/usr/bin/env bash
set -euo pipefail
: "${DATABASE_URL:?DATABASE_URL is required}"
MAX_AGE_HOURS="${EVIDENCE_VERIFICATION_MAX_AGE_HOURS:-24}"
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -v hours="$MAX_AGE_HOURS" -AtX <<'SQL'
SELECT control_code,count(*),extract(epoch FROM (now()-min(created_at)))::bigint
FROM control_evidence_catalog
WHERE status='REGISTERED' AND created_at<now()-(:'hours'||' hours')::interval
GROUP BY control_code ORDER BY control_code;
SQL
