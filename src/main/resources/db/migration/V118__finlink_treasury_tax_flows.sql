CREATE TABLE IF NOT EXISTS finlink_treasury_event (
    id UUID PRIMARY KEY,
    event_ref VARCHAR(120) NOT NULL UNIQUE,
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    event_type VARCHAR(40) NOT NULL CHECK (event_type IN ('TRANSIT_FUND_MOVE','LIFMIS_INFORM','EASYTAX_SETTLEMENT','EASYTAX_ADJUSTMENT','ROADTAX_SETTLEMENT')),
    product_code VARCHAR(40) NOT NULL,
    business_date DATE NOT NULL,
    currency CHAR(3) NOT NULL,
    amount NUMERIC(24,4) NOT NULL CHECK (amount > 0),
    debit_account_code VARCHAR(80) REFERENCES control_ledger_account(account_code),
    credit_account_code VARCHAR(80) REFERENCES control_ledger_account(account_code),
    counterparty_ref VARCHAR(160),
    external_reference VARCHAR(160),
    status VARCHAR(24) NOT NULL CHECK (status IN ('RECORDED','POSTED','INFORMED','FAILED')),
    journal_id UUID REFERENCES control_journal(id),
    payload_hash CHAR(64) NOT NULL,
    requested_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    posted_at TIMESTAMPTZ,
    informed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_finlink_treasury_event_business_date
    ON finlink_treasury_event(event_type, product_code, business_date, status);

CREATE INDEX IF NOT EXISTS idx_finlink_treasury_event_journal
    ON finlink_treasury_event(journal_id);

INSERT INTO control_ledger_account(account_code, account_name, participant_code, currency, normal_side)
VALUES
    ('FINLINK_TRANSIT_CLEARING', 'Finlink transit clearing', 'FINLINK', 'LAK', 'DEBIT'),
    ('FINLINK_SETTLEMENT_PAYABLE', 'Finlink settlement payable', 'FINLINK', 'LAK', 'CREDIT'),
    ('FINLINK_LIFMIS_RECEIVABLE', 'Finlink LIFMIS receivable', 'FINLINK', 'LAK', 'DEBIT'),
    ('FINLINK_TAX_PAYABLE', 'Finlink tax payable', 'FINLINK', 'LAK', 'CREDIT'),
    ('FINLINK_ROADTAX_PAYABLE', 'Finlink road tax payable', 'FINLINK', 'LAK', 'CREDIT')
ON CONFLICT (account_code) DO NOTHING;
