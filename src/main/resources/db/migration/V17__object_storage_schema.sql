-- ============================================================
-- V17 · Object storage metadata topology marker
--
-- object_storage metadata belongs to the standalone archive PostgreSQL
-- database. The HOT database stores active payload rows only for the hot
-- retention window; large archived payload bytes live in MinIO/S3.
-- ============================================================

-- No HOT database DDL.
