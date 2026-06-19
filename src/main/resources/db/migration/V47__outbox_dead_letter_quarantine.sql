CREATE TABLE outbox_dead_letters (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL UNIQUE,
    schema_name VARCHAR(160),
    schema_version INTEGER,
    outbox_event_id BIGINT,
    transfer_ref VARCHAR(128),
    payload_json TEXT NOT NULL,
    payload_sha256 CHAR(64) NOT NULL,
    failure_type VARCHAR(160) NOT NULL,
    failure_message VARCHAR(1000),
    status VARCHAR(32) NOT NULL,
    failure_count INTEGER NOT NULL DEFAULT 1,
    first_failed_at TIMESTAMP NOT NULL,
    last_failed_at TIMESTAMP NOT NULL,
    replay_requested_by VARCHAR(160),
    replay_requested_at TIMESTAMP,
    replay_approved_by VARCHAR(160),
    replay_approved_at TIMESTAMP,
    replayed_by VARCHAR(160),
    replayed_at TIMESTAMP,
    discarded_by VARCHAR(160),
    discarded_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_outbox_dlq_status CHECK (status IN ('QUARANTINED','REPLAY_REQUESTED','APPROVED','REPLAYED','DISCARDED')),
    CONSTRAINT ck_outbox_dlq_four_eyes CHECK (
      replay_approved_by IS NULL OR replay_requested_by IS NULL OR replay_approved_by <> replay_requested_by
    )
);
CREATE INDEX idx_outbox_dlq_status_failed ON outbox_dead_letters(status, last_failed_at DESC);
CREATE INDEX idx_outbox_dlq_outbox_event ON outbox_dead_letters(outbox_event_id);
