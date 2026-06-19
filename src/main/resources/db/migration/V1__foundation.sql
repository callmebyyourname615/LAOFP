-- ============================================================
-- V1 · Foundation: extensions, shared functions, archive schema
-- ============================================================

-- pgcrypto: SHA-256 hashing for API keys / credentials
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ── set_updated_at ─────────────────────────────────────────
-- Generic trigger function used by every table that has
-- an updated_at column.  Attach with:
--   CREATE TRIGGER trg_<table>_updated_at
--     BEFORE UPDATE ON <table>
--     FOR EACH ROW EXECUTE FUNCTION set_updated_at();
-- ───────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$;

-- Archive tables are owned by the standalone archive PostgreSQL database
-- (`switching_archive`), bootstrapped by scripts/init-archive-db.sql.
-- The HOT database intentionally keeps only the default `public` schema.
