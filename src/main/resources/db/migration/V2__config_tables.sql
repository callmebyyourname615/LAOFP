-- ============================================================
-- V2 · Config tables
--     participants, participant_limits,
--     routing_rules, connector_configs,
--     connector_credentials, connector_rate_limits
-- No partitioning — low-volume, long-lived config data.
-- ============================================================

-- ── participants ────────────────────────────────────────────
CREATE TABLE participants (
    id                  BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    bank_code           VARCHAR(32)     NOT NULL,
    bank_name           VARCHAR(255)    NOT NULL,
    status              VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    participant_type    VARCHAR(32)     NOT NULL DEFAULT 'DIRECT',
    country             VARCHAR(8)      NOT NULL DEFAULT 'LA',
    currency            VARCHAR(8)      NOT NULL DEFAULT 'LAK',
    swift_bic           VARCHAR(20)     NULL,
    settlement_account  VARCHAR(60)     NULL,
    max_daily_limit     NUMERIC(18,2)   NULL,
    max_single_txn      NUMERIC(18,2)   NULL,
    contact_email       VARCHAR(100)    NULL,
    contact_phone       VARCHAR(30)     NULL,
    created_at          TIMESTAMP(3)    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP(3)    NULL,
    CONSTRAINT uq_participants_bank_code UNIQUE (bank_code)
);

CREATE TRIGGER trg_participants_updated_at
    BEFORE UPDATE ON participants
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── participant_limits ──────────────────────────────────────
-- Per-participant limit overrides (override the global defaults).
CREATE TABLE participant_limits (
    id              BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    bank_code       VARCHAR(32)     NOT NULL REFERENCES participants(bank_code),
    limit_type      VARCHAR(40)     NOT NULL,   -- DAILY_AMOUNT | DAILY_COUNT | PER_TXN_AMOUNT | HOURLY_AMOUNT
    currency        VARCHAR(8)      NOT NULL DEFAULT 'LAK',
    limit_value     NUMERIC(18,2)   NOT NULL,
    period_type     VARCHAR(20)     NOT NULL DEFAULT 'DAILY',
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    effective_from  DATE            NULL,
    effective_to    DATE            NULL,
    created_at      TIMESTAMP(3)    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP(3)    NULL,
    CONSTRAINT uq_participant_limits UNIQUE (bank_code, limit_type, currency)
);

CREATE TRIGGER trg_participant_limits_updated_at
    BEFORE UPDATE ON participant_limits
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── routing_rules ───────────────────────────────────────────
CREATE TABLE routing_rules (
    id               BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    route_code       VARCHAR(128) NOT NULL,
    source_bank      VARCHAR(32)  NOT NULL,
    destination_bank VARCHAR(32)  NOT NULL,
    message_type     VARCHAR(32)  NOT NULL,
    connector_name   VARCHAR(128) NOT NULL,
    priority         INT          NOT NULL DEFAULT 1,
    enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP(3) NULL,
    CONSTRAINT uq_routing_rules_route_code UNIQUE (route_code)
);

CREATE INDEX idx_routing_rules_lookup
    ON routing_rules(source_bank, destination_bank, message_type, enabled, priority);

CREATE TRIGGER trg_routing_rules_updated_at
    BEFORE UPDATE ON routing_rules
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── connector_configs ───────────────────────────────────────
CREATE TABLE connector_configs (
    id                    BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    connector_name        VARCHAR(128) NOT NULL,
    bank_code             VARCHAR(32)  NOT NULL,
    connector_type        VARCHAR(32)  NOT NULL,
    endpoint_url          VARCHAR(512) NULL,
    timeout_ms            INT          NOT NULL DEFAULT 5000,
    enabled               BOOLEAN      NOT NULL DEFAULT TRUE,
    force_reject          BOOLEAN      NOT NULL DEFAULT FALSE,
    reject_reason_code    VARCHAR(32)  NULL,
    reject_reason_message VARCHAR(512) NULL,
    created_at            TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP(3) NULL,
    CONSTRAINT uq_connector_configs_name UNIQUE (connector_name)
);

CREATE INDEX idx_connector_configs_bank_code ON connector_configs(bank_code);
CREATE INDEX idx_connector_configs_enabled   ON connector_configs(enabled);

CREATE TRIGGER trg_connector_configs_updated_at
    BEFORE UPDATE ON connector_configs
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── connector_credentials ───────────────────────────────────
-- Encrypted credentials for each connector (API keys, certs, OAuth secrets).
CREATE TABLE connector_credentials (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    connector_name  VARCHAR(128) NOT NULL REFERENCES connector_configs(connector_name),
    credential_type VARCHAR(40)  NOT NULL,   -- API_KEY | MTLS_CERT | OAUTH_SECRET | BASIC_AUTH
    credential_key  VARCHAR(100) NOT NULL,   -- logical key name
    encrypted_value TEXT         NOT NULL,   -- AES-256 or base64-wrapped value
    key_ref         VARCHAR(64)  NULL,       -- reference to KMS key used for encryption
    expires_at      TIMESTAMP(3) NULL,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP(3) NULL,
    CONSTRAINT uq_connector_credentials UNIQUE (connector_name, credential_type, credential_key)
);

CREATE TRIGGER trg_connector_credentials_updated_at
    BEFORE UPDATE ON connector_credentials
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── connector_rate_limits ───────────────────────────────────
CREATE TABLE connector_rate_limits (
    id                  BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    connector_name      VARCHAR(128) NOT NULL REFERENCES connector_configs(connector_name),
    limit_type          VARCHAR(20)  NOT NULL,   -- RPS | RPM | RPH | DAILY
    limit_value         INT          NOT NULL,
    burst_value         INT          NULL,
    window_seconds      INT          NOT NULL DEFAULT 1,
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP(3) NULL,
    CONSTRAINT uq_connector_rate_limits UNIQUE (connector_name, limit_type)
);

CREATE TRIGGER trg_connector_rate_limits_updated_at
    BEFORE UPDATE ON connector_rate_limits
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
