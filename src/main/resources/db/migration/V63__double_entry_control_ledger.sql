CREATE TABLE IF NOT EXISTS control_ledger_account (
    account_code VARCHAR(80) PRIMARY KEY,
    account_name VARCHAR(200) NOT NULL,
    participant_code VARCHAR(32),
    currency CHAR(3) NOT NULL,
    normal_side VARCHAR(6) NOT NULL CHECK (normal_side IN ('DEBIT','CREDIT')),
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS control_journal (
    id UUID PRIMARY KEY,
    source_type VARCHAR(80) NOT NULL,
    source_reference VARCHAR(160) NOT NULL,
    business_date DATE NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('OPEN','POSTED','REVERSED')),
    evidence_hash CHAR(64) NOT NULL,
    created_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    posted_at TIMESTAMPTZ,
    reversed_by_journal_id UUID REFERENCES control_journal(id),
    UNIQUE(source_type, source_reference, currency)
);

CREATE TABLE IF NOT EXISTS control_journal_entry (
    id UUID PRIMARY KEY,
    journal_id UUID NOT NULL REFERENCES control_journal(id) ON DELETE RESTRICT,
    line_no INTEGER NOT NULL CHECK (line_no > 0),
    account_code VARCHAR(80) NOT NULL REFERENCES control_ledger_account(account_code),
    side VARCHAR(6) NOT NULL CHECK (side IN ('DEBIT','CREDIT')),
    amount NUMERIC(24,4) NOT NULL CHECK (amount > 0),
    narrative VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(journal_id, line_no)
);
CREATE INDEX IF NOT EXISTS idx_control_journal_business_date ON control_journal(business_date, status);
CREATE INDEX IF NOT EXISTS idx_control_entry_account ON control_journal_entry(account_code, created_at DESC);

CREATE OR REPLACE FUNCTION assert_control_journal_balanced() RETURNS trigger AS $$
DECLARE
    debit_total NUMERIC(24,4);
    credit_total NUMERIC(24,4);
    line_count INTEGER;
BEGIN
    IF NEW.status = 'POSTED' AND OLD.status <> 'POSTED' THEN
        SELECT COALESCE(sum(amount) FILTER (WHERE side='DEBIT'),0),
               COALESCE(sum(amount) FILTER (WHERE side='CREDIT'),0),
               count(*)
          INTO debit_total, credit_total, line_count
          FROM control_journal_entry WHERE journal_id = NEW.id;
        IF line_count < 2 OR debit_total <> credit_total THEN
            RAISE EXCEPTION 'journal % is not balanced: debit %, credit %, lines %', NEW.id, debit_total, credit_total, line_count;
        END IF;
        NEW.posted_at := COALESCE(NEW.posted_at, now());
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_control_journal_balance ON control_journal;
CREATE TRIGGER trg_control_journal_balance
BEFORE UPDATE OF status ON control_journal
FOR EACH ROW EXECUTE FUNCTION assert_control_journal_balanced();

CREATE OR REPLACE FUNCTION prevent_posted_journal_entry_mutation() RETURNS trigger AS $$
DECLARE journal_status VARCHAR(16);
BEGIN
    SELECT status INTO journal_status FROM control_journal WHERE id = COALESCE(OLD.journal_id, NEW.journal_id);
    IF journal_status IN ('POSTED','REVERSED') THEN
        RAISE EXCEPTION 'entries of a posted/reversed journal are immutable';
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_control_entry_immutable ON control_journal_entry;
CREATE TRIGGER trg_control_entry_immutable
BEFORE UPDATE OR DELETE ON control_journal_entry
FOR EACH ROW EXECUTE FUNCTION prevent_posted_journal_entry_mutation();
