#!/usr/bin/env bash
set -euo pipefail
: "${DB_URL:?DB_URL is required}"
: "${FLYWAY_USERNAME:?FLYWAY_USERNAME is required}"
: "${FLYWAY_PASSWORD:?FLYWAY_PASSWORD is required}"
: "${DB_USERNAME:?DB_USERNAME is required}"
[[ "$DB_USERNAME" =~ ^[a-zA-Z_][a-zA-Z0-9_]*$ ]] || { echo 'Unsafe DB_USERNAME' >&2; exit 2; }
export PGPASSWORD="$FLYWAY_PASSWORD"
psql "$DB_URL" -U "$FLYWAY_USERNAME" -v ON_ERROR_STOP=1 <<SQL
REVOKE UPDATE, DELETE, TRUNCATE ON TABLE audit_logs FROM PUBLIC;
REVOKE UPDATE, DELETE, TRUNCATE ON TABLE audit_logs FROM "$DB_USERNAME";
GRANT SELECT, INSERT ON TABLE audit_logs TO "$DB_USERNAME";
GRANT USAGE, SELECT ON SEQUENCE audit_logs_id_seq TO "$DB_USERNAME";
SQL
psql "$DB_URL" -U "$FLYWAY_USERNAME" -v ON_ERROR_STOP=1 -Atc "SELECT privilege_type FROM information_schema.role_table_grants WHERE grantee='$DB_USERNAME' AND table_name='audit_logs' ORDER BY 1"
