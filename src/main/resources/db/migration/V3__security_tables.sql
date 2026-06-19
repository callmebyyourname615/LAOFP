-- ============================================================
-- V3 · Security tables
--     api_keys, oauth_clients, psp_certificates
-- No partitioning.
-- ============================================================

-- ── api_keys ────────────────────────────────────────────────
-- key_value stores SHA-256 hex digest of the raw API key.
-- key_prefix stores first 12 chars of the original key (display only).
CREATE TABLE api_keys (
    id           BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    key_value    VARCHAR(64)  NOT NULL,   -- SHA-256 hex (64 chars)
    key_prefix   VARCHAR(16)  NULL,       -- first 12 chars of the original key
    name         VARCHAR(128) NOT NULL,
    role         VARCHAR(32)  NOT NULL,   -- ADMIN | OPS | BANK
    bank_code    VARCHAR(32)  NULL,       -- populated for BANK role
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    expires_at   TIMESTAMP(3) NULL,
    created_at   TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    last_used_at TIMESTAMP(3) NULL,
    CONSTRAINT uq_api_keys_value UNIQUE (key_value)
);

CREATE INDEX idx_api_keys_enabled ON api_keys(enabled);

-- ── oauth_clients ───────────────────────────────────────────
CREATE TABLE oauth_clients (
    client_id          VARCHAR(128) NOT NULL,
    psp_id             VARCHAR(32)  NOT NULL,
    client_secret_hash VARCHAR(64)  NOT NULL,
    tier               VARCHAR(16)  NOT NULL,
    scopes             TEXT         NOT NULL,
    status             VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    expires_at         TIMESTAMP(3) NULL,
    created_at         TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_oauth_clients PRIMARY KEY (client_id),
    CONSTRAINT fk_oauth_clients_psp FOREIGN KEY (psp_id) REFERENCES participants(bank_code)
);

CREATE INDEX idx_oauth_clients_psp_status    ON oauth_clients(psp_id, status);
CREATE INDEX idx_oauth_clients_status_expiry ON oauth_clients(status, expires_at);

-- ── psp_certificates ────────────────────────────────────────
-- SHA-256 fingerprints of X.509 client certificates for mTLS.
CREATE TABLE psp_certificates (
    cert_id          VARCHAR(36)  NOT NULL,
    psp_id           VARCHAR(32)  NOT NULL,
    cert_fingerprint VARCHAR(64)  NOT NULL,
    subject_dn       TEXT         NOT NULL,
    issued_at        TIMESTAMP(3) NOT NULL,
    expires_at       TIMESTAMP(3) NOT NULL,
    status           VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE | REVOKED
    created_at       TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_psp_certificates PRIMARY KEY (cert_id),
    CONSTRAINT uq_psp_certificates_fingerprint UNIQUE (cert_fingerprint),
    CONSTRAINT fk_psp_certificates_psp FOREIGN KEY (psp_id) REFERENCES participants(bank_code)
);

CREATE INDEX idx_psp_certificates_psp_status    ON psp_certificates(psp_id, status);
CREATE INDEX idx_psp_certificates_fingerprint   ON psp_certificates(cert_fingerprint);
CREATE INDEX idx_psp_certificates_status_expiry ON psp_certificates(status, expires_at);
