#!/usr/bin/env bash
set -euo pipefail
: "${DB_URL:?DB_URL is required}"
: "${DB_USERNAME:?DB_USERNAME is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"
tmp=$(mktemp); trap 'rm -f "$tmp"' EXIT
export PGPASSWORD="$DB_PASSWORD"
psql "$DB_URL" -U "$DB_USERNAME" -v ON_ERROR_STOP=1 --csv -c "SELECT id,event_type,reference_type,reference_id,actor,payload,created_at,previous_hash,entry_hash FROM audit_logs WHERE entry_hash IS NOT NULL ORDER BY id" > "$tmp"
python3 "$(dirname "$0")/verify-audit-chain.py" "$tmp"
