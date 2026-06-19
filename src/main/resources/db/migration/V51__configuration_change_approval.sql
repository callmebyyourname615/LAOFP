CREATE TABLE configuration_change_requests (
    id BIGSERIAL PRIMARY KEY,
    request_ref VARCHAR(64) NOT NULL UNIQUE,
    target_type VARCHAR(48) NOT NULL,
    target_key VARCHAR(160) NOT NULL,
    previous_value VARCHAR(512) NOT NULL,
    desired_value VARCHAR(512) NOT NULL,
    payload_sha256 CHAR(64) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    ticket_reference VARCHAR(160) NOT NULL,
    status VARCHAR(24) NOT NULL,
    requested_by VARCHAR(160) NOT NULL,
    requested_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    approved_by VARCHAR(160),
    approved_at TIMESTAMP,
    executed_by VARCHAR(160),
    executed_at TIMESTAMP,
    rejected_by VARCHAR(160),
    rejected_at TIMESTAMP,
    rejection_reason VARCHAR(1000),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_config_change_target CHECK (target_type IN ('PARTICIPANT_STATUS','CONNECTOR_ENABLED','CONNECTOR_FORCE_REJECT','ROUTING_RULE_ENABLED')),
    CONSTRAINT ck_config_change_status CHECK (status IN ('PENDING','APPROVED','EXECUTED','REJECTED','EXPIRED','STALE')),
    CONSTRAINT ck_config_change_four_eyes CHECK (approved_by IS NULL OR approved_by <> requested_by)
);
CREATE INDEX idx_config_change_status_created ON configuration_change_requests(status, requested_at DESC);
CREATE INDEX idx_config_change_target ON configuration_change_requests(target_type, target_key);
