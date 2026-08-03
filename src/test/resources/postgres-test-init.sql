-- The production migration V107 grants reporting privileges to the runtime role.
-- Create that role in disposable Testcontainers databases before Flyway runs.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'switching_app') THEN
        CREATE ROLE switching_app NOLOGIN;
    END IF;
END
$$;
