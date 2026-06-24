-- V107: Grant the application role privileges on the `reporting` schema.
--
-- V85 created the `reporting` schema (lookup directories + status aggregates)
-- and V94/V100 added functions and views inside it, but no migration ever
-- granted the runtime role (switching_app) USAGE/SELECT/INSERT/UPDATE on it.
-- That caused TPS POST /api/inquiries and GET /api/dashboard/overview to
-- 500 with "permission denied for schema reporting" the first time they
-- touched reporting.current_inquiry_status or reporting.sync_*_status().

GRANT USAGE ON SCHEMA reporting TO switching_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA reporting TO switching_app;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA reporting TO switching_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA reporting TO switching_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA reporting
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO switching_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA reporting
    GRANT EXECUTE ON FUNCTIONS TO switching_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA reporting
    GRANT USAGE, SELECT ON SEQUENCES TO switching_app;
