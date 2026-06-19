#!/usr/bin/env bash
set -euo pipefail
: "${DATABASE_URL:?DATABASE_URL is required}"
WARNING_DAYS="${CRYPTO_ROTATION_WARNING_DAYS:-30}"
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -v warning_days="$WARNING_DAYS" -AtX <<'SQL'
SELECT 'overdue',count(*) FROM cryptographic_asset_inventory
 WHERE status IN ('ACTIVE','ROTATING') AND next_rotation_at<now()
UNION ALL
SELECT 'due_soon',count(*) FROM cryptographic_asset_inventory
 WHERE status IN ('ACTIVE','ROTATING') AND next_rotation_at BETWEEN now() AND now()+(:'warning_days'||' days')::interval
UNION ALL
SELECT 'expired',count(*) FROM cryptographic_asset_inventory
 WHERE status='ACTIVE' AND expires_at IS NOT NULL AND expires_at<now()
UNION ALL
SELECT 'rotation_failed',count(*) FROM cryptographic_rotation_plan
 WHERE status='FAILED' AND created_at>=now()-interval '24 hours';
SQL
