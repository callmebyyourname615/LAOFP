-- V37: Bill Payment Service (P16)
-- billers, bill_tokens, bill_payments

-- ── billers ───────────────────────────────────────────────────────────────────
CREATE TABLE billers (
    biller_id       BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    biller_code     VARCHAR(50)   NOT NULL,
    biller_name     VARCHAR(200)  NOT NULL,
    category        VARCHAR(20)   NOT NULL,
    api_url         VARCHAR(500)  NOT NULL,
    api_key_hash    VARCHAR(64)   NOT NULL,
    timeout_seconds INT           NOT NULL DEFAULT 30,
    status          VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP(3)  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_billers_code       UNIQUE (biller_code),
    CONSTRAINT chk_biller_category   CHECK (category IN ('UTILITY','TELECOM','GOVERNMENT','LOAN','INSURANCE')),
    CONSTRAINT chk_biller_status     CHECK (status   IN ('ACTIVE','INACTIVE'))
);

CREATE INDEX idx_billers_status ON billers(status);

-- ── bill_tokens ───────────────────────────────────────────────────────────────
-- One-time 10-minute fetch tokens issued by the switching hub
CREATE TABLE bill_tokens (
    token_id      BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    biller_id     BIGINT        NOT NULL REFERENCES billers(biller_id),
    bill_ref      VARCHAR(200)  NOT NULL,
    bill_amount   DECIMAL(20,4) NOT NULL,
    due_date      DATE,
    customer_name VARCHAR(200),
    details       TEXT,          -- JSON blob from biller
    fetched_at    TIMESTAMP(3)  NOT NULL DEFAULT NOW(),
    expires_at    TIMESTAMP(3)  NOT NULL,
    used          BOOLEAN       NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_bill_token_amount CHECK (bill_amount > 0)
);

CREATE INDEX idx_bill_tokens_biller     ON bill_tokens(biller_id, bill_ref);
CREATE INDEX idx_bill_tokens_expires    ON bill_tokens(expires_at) WHERE NOT used;

-- ── bill_payments ─────────────────────────────────────────────────────────────
CREATE TABLE bill_payments (
    payment_id     BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    token_id       BIGINT        NOT NULL REFERENCES bill_tokens(token_id),
    biller_id      BIGINT        NOT NULL REFERENCES billers(biller_id),
    bill_ref       VARCHAR(200)  NOT NULL,
    txn_ref        VARCHAR(200),            -- transaction_ref in transactions table
    paying_psp_id  VARCHAR(32)   NOT NULL,
    receipt_number VARCHAR(200),
    amount         DECIMAL(20,4) NOT NULL,
    status         VARCHAR(10)   NOT NULL DEFAULT 'INITIATED',
    initiated_at   TIMESTAMP(3)  NOT NULL DEFAULT NOW(),
    confirmed_at   TIMESTAMP(3),

    CONSTRAINT chk_bill_payment_status CHECK (status IN ('INITIATED','CONFIRMED','FAILED'))
);

CREATE INDEX idx_bill_payments_biller_ref ON bill_payments(biller_id, bill_ref, status, initiated_at DESC);
CREATE INDEX idx_bill_payments_token      ON bill_payments(token_id);

-- ── demo seed billers (used by integration tests) ────────────────────────────
INSERT INTO billers (biller_code, biller_name, category, api_url, api_key_hash, timeout_seconds, status)
VALUES
  ('EDL-001', 'Electricite du Laos', 'UTILITY',
   'http://mock-biller:9099/biller/edl',  'sha256-edl-hash',  30, 'ACTIVE'),
  ('LTC-001', 'Lao Telecom',          'TELECOM',
   'http://mock-biller:9099/biller/ltc',  'sha256-ltc-hash',  30, 'ACTIVE'),
  ('GOV-001', 'Lao Tax Department',   'GOVERNMENT',
   'http://mock-biller:9099/biller/gov',  'sha256-gov-hash',  30, 'INACTIVE');
