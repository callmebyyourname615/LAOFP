-- ============================================================
-- V11 · Audit log table
-- ============================================================

CREATE TABLE audit_logs (
    id             BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_type     VARCHAR(60)  NOT NULL,
    reference_type VARCHAR(40)  NOT NULL,
    reference_id   VARCHAR(80)  NULL,
    actor          VARCHAR(60)  NOT NULL,
    payload        TEXT         NULL,
    business_date  DATE         NOT NULL DEFAULT CURRENT_DATE,
    created_at     TIMESTAMP(3) NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_reference
    ON audit_logs(reference_type, reference_id);
CREATE INDEX idx_audit_logs_event_type
    ON audit_logs(event_type, created_at);
CREATE INDEX idx_audit_logs_actor
    ON audit_logs(actor, created_at);
CREATE INDEX idx_audit_logs_business_date
    ON audit_logs(business_date);
