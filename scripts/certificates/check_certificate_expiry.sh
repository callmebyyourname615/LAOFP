#!/usr/bin/env bash
set -euo pipefail
: "${DB_URL:?DB_URL required}"
warning_days="${CERT_WARNING_DAYS:-30}"
psql "$DB_URL" -v ON_ERROR_STOP=1 -v days="$warning_days" -Atc "SELECT participant_code||E'\t'||certificate_type||E'\t'||not_after FROM participant_certificate WHERE status IN ('ACTIVE','OVERLAP') AND not_after < now()+(:'days'||' days')::interval ORDER BY not_after"
expired=$(psql "$DB_URL" -v ON_ERROR_STOP=1 -Atc "SELECT count(*) FROM participant_certificate WHERE status='ACTIVE' AND not_after<=now()")
[[ "$expired" == "0" ]] || { echo "expired active certificates: $expired" >&2; exit 1; }
