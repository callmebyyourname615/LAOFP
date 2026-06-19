#!/bin/bash
# Creates least-privilege DB users for the switching app.
# Runs once on first PostgreSQL container start (docker-entrypoint-initdb.d).
# Env vars injected from docker-compose postgres service environment block.
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    -- Flyway user: needs DDL to run schema migrations
    DO \$\$
    BEGIN
        IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'switching_flyway') THEN
            CREATE ROLE switching_flyway LOGIN PASSWORD '${FLYWAY_PASSWORD:-switching_flyway_password_change_me}';
        END IF;
    END
    \$\$;
    GRANT ALL PRIVILEGES ON DATABASE switching_db TO switching_flyway;
    GRANT ALL ON SCHEMA public TO switching_flyway;

    -- Streaming replication user for the hot read replica.
    DO \$\$
    BEGIN
        IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '${REPLICATION_USER:-switching_replicator}') THEN
            CREATE ROLE ${REPLICATION_USER:-switching_replicator}
                WITH REPLICATION LOGIN PASSWORD '${REPLICATION_PASSWORD:-switching_replicator_password_change_me}';
        END IF;
    END
    \$\$;

    -- App user: DML only — cannot ALTER or DROP schema
    DO \$\$
    BEGIN
        IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'switching_app') THEN
            CREATE ROLE switching_app LOGIN PASSWORD '${DB_APP_PASSWORD:-switching_app_password_change_me}';
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

if ! grep -q "host replication ${REPLICATION_USER:-switching_replicator}" "$PGDATA/pg_hba.conf"; then
    echo "host replication ${REPLICATION_USER:-switching_replicator} all md5" >> "$PGDATA/pg_hba.conf"
fi

echo "[init-db-users] Created roles: switching_app (DML only), switching_flyway (DDL), switching_replicator (replication)"
