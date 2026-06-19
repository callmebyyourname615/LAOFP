-- ============================================================
-- V29 · VPA (Virtual Payment Address) registrations
--
-- Maps a human-friendly alias (phone, NID, email, QR, merchant)
-- to a PSP account.  Only one ACTIVE row is allowed per
-- (vpa_type, vpa_value) pair — enforced by a partial unique index.
-- ============================================================

CREATE TABLE vpa_registrations (
    id            BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    vpa_id        VARCHAR(36)  NOT NULL,                   -- public UUID handle
    vpa_type      VARCHAR(20)  NOT NULL,                   -- MSISDN|NATIONAL_ID|EMAIL|QR_STATIC|MERCHANT_ID
    vpa_value     VARCHAR(200) NOT NULL,
    psp_id        VARCHAR(32)  NOT NULL REFERENCES participants(bank_code),
    account_ref   VARCHAR(200) NOT NULL,                   -- account number / wallet ref
    account_type  VARCHAR(20)  NOT NULL DEFAULT 'BANK_ACCOUNT',  -- BANK_ACCOUNT|WALLET
    display_name  VARCHAR(200) NULL,
    is_primary    BOOLEAN      NOT NULL DEFAULT TRUE,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE|INACTIVE
    created_at    TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP(3) NULL,
    CONSTRAINT uq_vpa_id UNIQUE (vpa_id)
);

-- Only one active entry per (type, value) across all PSPs
CREATE UNIQUE INDEX uq_vpa_active_value
    ON vpa_registrations(vpa_type, vpa_value)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_vpa_psp
    ON vpa_registrations(psp_id, status);

CREATE INDEX idx_vpa_type_value
    ON vpa_registrations(vpa_type, vpa_value, status);

CREATE TRIGGER trg_vpa_registrations_updated_at
    BEFORE UPDATE ON vpa_registrations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
