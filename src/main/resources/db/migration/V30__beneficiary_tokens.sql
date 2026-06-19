-- ============================================================
-- V30 · Beneficiary tokens
--
-- One-time-use tokens issued on VPA lookup.  The transfer
-- initiation call validates and consumes the token, ensuring
-- the resolved account can't be replayed.  TTL = 5 minutes
-- (enforced in application layer via expires_at).
-- ============================================================

CREATE TABLE beneficiary_tokens (
    token_id    VARCHAR(36)  NOT NULL PRIMARY KEY,   -- UUID, opaque to clients
    vpa_id      VARCHAR(36)  NOT NULL REFERENCES vpa_registrations(vpa_id),
    issued_at   TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    expires_at  TIMESTAMP(3) NOT NULL,               -- issued_at + token_ttl_seconds
    used        BOOLEAN      NOT NULL DEFAULT FALSE,
    used_at     TIMESTAMP(3) NULL
);

CREATE INDEX idx_beneficiary_tokens_vpa
    ON beneficiary_tokens(vpa_id);

-- Fast lookup for expiry sweeps and unused-token validation
CREATE INDEX idx_beneficiary_tokens_expiry
    ON beneficiary_tokens(expires_at, used)
    WHERE used = FALSE;
