CREATE TABLE IF NOT EXISTS drs_dispute_attachments (
    attachment_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dispute_id BIGINT NOT NULL REFERENCES disputes(dispute_id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    uploaded_by VARCHAR(100) NOT NULL,
    uploaded_at TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    description TEXT,
    payload BYTEA NOT NULL,
    CONSTRAINT ck_drs_attachment_size CHECK (file_size_bytes > 0)
);

CREATE INDEX IF NOT EXISTS idx_drs_attachments_dispute
    ON drs_dispute_attachments(dispute_id, uploaded_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS ux_drs_attachments_dispute_sha
    ON drs_dispute_attachments(dispute_id, sha256);
