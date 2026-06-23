#!/bin/bash
# Creates least-privilege DB users for the switching app.
# Runs once on first PostgreSQL container start (docker-entrypoint-initdb.d).
# Env vars injected from docker-compose postgres service environment block.
set -euo pipefail

: "${POSTGRES_USER:?POSTGRES_USER is required}"
: "${POSTGRES_DB:?POSTGRES_DB is required}"
: "${FLYWAY_PASSWORD:?FLYWAY_PASSWORD is required}"
: "${REPLICATION_PASSWORD:?REPLICATION_PASSWORD is required}"
: "${DB_APP_PASSWORD:?DB_APP_PASSWORD is required}"

REPLICATION_USER="${REPLICATION_USER:-switching_replicator}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    -- Flyway user: needs DDL to run schema migrations
    DO \$\$
    BEGIN
        IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'switching_flyway') THEN
            CREATE ROLE switching_flyway LOGIN PASSWORD '${FLYWAY_PASSWORD}';
        END IF;
    END
    \$\$;
    GRANT ALL PRIVILEGES ON DATABASE switching_db TO switching_flyway;
    GRANT ALL ON SCHEMA public TO switching_flyway;

    -- Streaming replication user for the hot read replica.
    DO \$\$
    BEGIN
        IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '${REPLICATION_USER}') THEN
            CREATE ROLE ${REPLICATION_USER}
                WITH REPLICATION LOGIN PASSWORD '${REPLICATION_PASSWORD}';
        END IF;
    END
    \$\$;

    -- App user: DML only — cannot ALTER or DROP schema
    DO \$\$
    BEGIN
        IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'switching_app') THEN
            CREATE ROLE switching_app LOGIN PASSWORD '${DB_APP_PASSWORD}';
        END IF;
    END
    \$\$;
    GRANT CONNECT ON DATABASE switching_db TO switching_app;
    GRANT USAGE ON SCHEMA public TO switching_app;
    GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO switching_app;
    ALTER DEFAULT PRIVILEGES FOR ROLE switching_flyway IN SCHEMA public
        GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO switching_app;
    GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO switching_app;
    ALTER DEFAULT PRIVILEGES FOR ROLE switching_flyway IN SCHEMA public
        GRANT USAGE, SELECT ON SEQUENCES TO switching_app;
EOSQL

if ! grep -q "host replication ${REPLICATION_USER}" "$PGDATA/pg_hba.conf"; then
    echo "host replication ${REPLICATION_USER} all md5" >> "$PGDATA/pg_hba.conf"
fi

echo "[init-db-users] Created roles: switching_app (DML only), switching_flyway (DDL), switching_replicator (replication)"
