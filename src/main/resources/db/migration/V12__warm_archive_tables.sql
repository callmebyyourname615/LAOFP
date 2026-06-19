-- ============================================================
-- V12 · Archive topology marker
--
-- Production-ready topology keeps archive tables out of the HOT database.
-- Archive tables are created in the standalone archive PostgreSQL database
-- (`switching_archive`) by scripts/init-archive-db.sql.
-- ============================================================

-- No HOT database DDL.
