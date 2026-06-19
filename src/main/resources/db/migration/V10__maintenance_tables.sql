-- ============================================================
-- V10 · Maintenance and scheduler tables
--
--   archive_jobs               — tracks 90-day archival runs
--   partition_maintenance_logs — partition create/drop audit
--   scheduler_locks            — distributed lock for scheduled jobs
-- ============================================================

-- ── archive_jobs ─────────────────────────────────────────────
CREATE TABLE archive_jobs (
    id                BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_ref           VARCHAR(80)  NOT NULL,
    job_type          VARCHAR(30)  NOT NULL,   -- DAILY_ARCHIVE | PARTITION_DROP | PARTITION_CREATE
    table_name        VARCHAR(100) NOT NULL,
    archive_from      DATE         NOT NULL,
    archive_to        DATE         NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    -- PENDING | RUNNING | COMPLETED | FAILED
    rows_archived     BIGINT       NULL DEFAULT 0,
    rows_verified     BIGINT       NULL DEFAULT 0,
    error_message     TEXT         NULL,
    started_at        TIMESTAMP(3) NULL,
    completed_at      TIMESTAMP(3) NULL,
    created_at        TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP(3) NULL,
    CONSTRAINT uq_archive_jobs_ref UNIQUE (job_ref)
);

CREATE INDEX idx_archive_jobs_status    ON archive_jobs(status, created_at);
CREATE INDEX idx_archive_jobs_table_date ON archive_jobs(table_name, archive_from);

CREATE TRIGGER trg_archive_jobs_updated_at
    BEFORE UPDATE ON archive_jobs
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── partition_maintenance_logs ───────────────────────────────
-- Audit trail of every CREATE/DROP PARTITION operation.
CREATE TABLE partition_maintenance_logs (
    id             BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    table_name     VARCHAR(100) NOT NULL,
    partition_name VARCHAR(120) NOT NULL,
    operation      VARCHAR(20)  NOT NULL,   -- CREATE | DROP | ATTACH | DETACH
    partition_date DATE         NOT NULL,
    success        BOOLEAN      NOT NULL DEFAULT FALSE,
    error_message  TEXT         NULL,
    executed_at    TIMESTAMP(3) NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_partition_maint_table   ON partition_maintenance_logs(table_name, partition_date);
CREATE INDEX idx_partition_maint_date    ON partition_maintenance_logs(executed_at);

-- ── scheduler_locks ──────────────────────────────────────────
-- Distributed lock table (ShedLock-compatible schema).
-- Used to ensure exactly-one execution of scheduled tasks
-- across multiple application instances.
CREATE TABLE scheduler_locks (
    lock_name   VARCHAR(64)  NOT NULL,
    lock_until  TIMESTAMP(3) NOT NULL,
    locked_at   TIMESTAMP(3) NOT NULL,
    locked_by   VARCHAR(255) NOT NULL,
    CONSTRAINT pk_scheduler_locks PRIMARY KEY (lock_name)
);
