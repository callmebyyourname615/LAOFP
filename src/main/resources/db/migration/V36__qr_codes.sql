-- V36: QR Code Service (P15)
-- Stores both STATIC (reusable, no amount) and DYNAMIC (single-use, fixed amount) QR codes.

CREATE TABLE qr_codes (
    id           BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    qr_id        VARCHAR(36)  NOT NULL,
    merchant_id  VARCHAR(100) NOT NULL,
    psp_id       VARCHAR(32)  NOT NULL,
    qr_type      VARCHAR(10)  NOT NULL,          -- 'STATIC' | 'DYNAMIC'
    payload_text TEXT         NOT NULL,
    amount       DECIMAL(20,4),                  -- NULL for STATIC
    currency     VARCHAR(3)   NOT NULL DEFAULT 'LAK',
    txn_ref      VARCHAR(100),                   -- NULL for STATIC; UNIQUE for DYNAMIC
    expires_at   TIMESTAMP(3),                   -- NULL = never (STATIC)
    used         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP(3) NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_qr_codes_qr_id     UNIQUE (qr_id),
    CONSTRAINT uq_qr_codes_txn_ref   UNIQUE (txn_ref),
    CONSTRAINT chk_qr_type           CHECK (qr_type IN ('STATIC', 'DYNAMIC'))
);

CREATE INDEX idx_qr_codes_merchant ON qr_codes(merchant_id, psp_id);
CREATE INDEX idx_qr_codes_created  ON qr_codes(created_at DESC);
