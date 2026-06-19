CREATE TABLE IF NOT EXISTS payment_idempotency_record (
    id UUID PRIMARY KEY,
    participant_code VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    transaction_reference VARCHAR(160),
    status VARCHAR(16) NOT NULL CHECK (status IN ('CLAIMED','COMPLETED','FAILED','EXPIRED')),
    response_hash CHAR(64),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    UNIQUE(participant_code,idempotency_key)
);
CREATE TABLE IF NOT EXISTS payment_duplicate_fingerprint (
    fingerprint CHAR(64) PRIMARY KEY,
    participant_code VARCHAR(32) NOT NULL,
    product_code VARCHAR(64) NOT NULL,
    transaction_reference VARCHAR(160) NOT NULL UNIQUE,
    amount NUMERIC(24,4) NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL,
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_duplicate_fingerprint_expiry ON payment_duplicate_fingerprint(expires_at);
CREATE TABLE IF NOT EXISTS payment_finality_record (
    id UUID PRIMARY KEY,
    transaction_reference VARCHAR(160) NOT NULL UNIQUE,
    participant_code VARCHAR(32) NOT NULL,
    finality_status VARCHAR(20) NOT NULL CHECK (finality_status IN ('PROVISIONAL','FINAL','REVERSED_BY_EXCEPTION')),
    finality_reason VARCHAR(500) NOT NULL,
    finalized_at TIMESTAMPTZ,
    evidence_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS payment_reversal_request (
    id UUID PRIMARY KEY,
    transaction_reference VARCHAR(160) NOT NULL REFERENCES payment_finality_record(transaction_reference),
    reversal_reference VARCHAR(160) NOT NULL UNIQUE,
    reason_code VARCHAR(64) NOT NULL,
    reason_detail VARCHAR(1000) NOT NULL,
    requested_by VARCHAR(120) NOT NULL,
    operations_approved_by VARCHAR(120),
    risk_approved_by VARCHAR(120),
    status VARCHAR(20) NOT NULL CHECK (status IN ('REQUESTED','PARTIALLY_APPROVED','APPROVED','REJECTED','EXECUTED','EXPIRED')),
    expires_at TIMESTAMPTZ NOT NULL,
    evidence_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    executed_at TIMESTAMPTZ,
    CHECK (operations_approved_by IS NULL OR operations_approved_by <> requested_by),
    CHECK (risk_approved_by IS NULL OR (risk_approved_by <> requested_by AND risk_approved_by <> operations_approved_by))
);

CREATE OR REPLACE FUNCTION prevent_final_payment_mutation() RETURNS trigger AS $$
BEGIN
    IF OLD.finality_status IN ('FINAL','REVERSED_BY_EXCEPTION') THEN
        IF NEW.transaction_reference<>OLD.transaction_reference OR NEW.participant_code<>OLD.participant_code OR NEW.evidence_hash<>OLD.evidence_hash THEN
            RAISE EXCEPTION 'final payment identity/evidence is immutable';
        END IF;
        IF OLD.finality_status='REVERSED_BY_EXCEPTION' AND NEW.finality_status<>OLD.finality_status THEN
            RAISE EXCEPTION 'reversed finality state is terminal';
        END IF;
    END IF;
    RETURN NEW;
END; $$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_payment_finality_immutable ON payment_finality_record;
CREATE TRIGGER trg_payment_finality_immutable BEFORE UPDATE ON payment_finality_record
FOR EACH ROW EXECUTE FUNCTION prevent_final_payment_mutation();
