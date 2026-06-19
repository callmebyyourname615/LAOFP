CREATE TABLE IF NOT EXISTS manual_financial_adjustment (
    id UUID PRIMARY KEY,
    adjustment_reference VARCHAR(160) NOT NULL UNIQUE,
    business_date DATE NOT NULL,
    currency CHAR(3) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    reason_detail VARCHAR(2000) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','EXECUTED','CANCELLED')),
    requested_by VARCHAR(120) NOT NULL,
    approved_by VARCHAR(120),
    executed_by VARCHAR(120),
    execution_journal_id UUID REFERENCES control_journal(id),
    evidence_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    submitted_at TIMESTAMPTZ,
    approved_at TIMESTAMPTZ,
    executed_at TIMESTAMPTZ,
    CHECK (approved_by IS NULL OR approved_by <> requested_by),
    CHECK (executed_by IS NULL OR (executed_by <> requested_by AND executed_by <> approved_by))
);
CREATE TABLE IF NOT EXISTS manual_financial_adjustment_line (
    id UUID PRIMARY KEY,
    adjustment_id UUID NOT NULL REFERENCES manual_financial_adjustment(id) ON DELETE RESTRICT,
    line_no INTEGER NOT NULL CHECK (line_no > 0),
    account_code VARCHAR(80) NOT NULL REFERENCES control_ledger_account(account_code),
    side VARCHAR(6) NOT NULL CHECK (side IN ('DEBIT','CREDIT')),
    amount NUMERIC(24,4) NOT NULL CHECK (amount > 0),
    narrative VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(adjustment_id,line_no)
);
CREATE TABLE IF NOT EXISTS manual_adjustment_approval_event (
    id UUID PRIMARY KEY,
    adjustment_id UUID NOT NULL REFERENCES manual_financial_adjustment(id),
    actor VARCHAR(120) NOT NULL,
    decision VARCHAR(16) NOT NULL CHECK (decision IN ('SUBMITTED','APPROVED','REJECTED','EXECUTED','CANCELLED')),
    comment VARCHAR(1000),
    evidence_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE OR REPLACE FUNCTION assert_manual_adjustment_balanced() RETURNS trigger AS $$
DECLARE debit_total NUMERIC(24,4); credit_total NUMERIC(24,4); line_count INTEGER;
BEGIN
    IF NEW.status='APPROVED' AND OLD.status<>'APPROVED' THEN
        SELECT COALESCE(sum(amount) FILTER (WHERE side='DEBIT'),0),
               COALESCE(sum(amount) FILTER (WHERE side='CREDIT'),0), count(*)
          INTO debit_total,credit_total,line_count
          FROM manual_financial_adjustment_line WHERE adjustment_id=NEW.id;
        IF line_count < 2 OR debit_total <> credit_total THEN
            RAISE EXCEPTION 'manual adjustment % is not balanced', NEW.id;
        END IF;
        NEW.approved_at := COALESCE(NEW.approved_at,now());
    END IF;
    RETURN NEW;
END; $$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_manual_adjustment_balanced ON manual_financial_adjustment;
CREATE TRIGGER trg_manual_adjustment_balanced BEFORE UPDATE OF status ON manual_financial_adjustment
FOR EACH ROW EXECUTE FUNCTION assert_manual_adjustment_balanced();

CREATE OR REPLACE FUNCTION prevent_executed_adjustment_mutation() RETURNS trigger AS $$
DECLARE current_status VARCHAR(20);
BEGIN
    SELECT status INTO current_status FROM manual_financial_adjustment WHERE id=COALESCE(OLD.adjustment_id,NEW.adjustment_id);
    IF current_status IN ('APPROVED','EXECUTED','CANCELLED') THEN
        RAISE EXCEPTION 'lines of approved/executed adjustment are immutable';
    END IF;
    RETURN COALESCE(NEW,OLD);
END; $$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_manual_adjustment_line_immutable ON manual_financial_adjustment_line;
CREATE TRIGGER trg_manual_adjustment_line_immutable BEFORE UPDATE OR DELETE ON manual_financial_adjustment_line
FOR EACH ROW EXECUTE FUNCTION prevent_executed_adjustment_mutation();
CREATE OR REPLACE FUNCTION prevent_manual_adjustment_event_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'manual adjustment approval events are append-only';
END; $$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_manual_adjustment_event_append_only ON manual_adjustment_approval_event;
CREATE TRIGGER trg_manual_adjustment_event_append_only BEFORE UPDATE OR DELETE ON manual_adjustment_approval_event
FOR EACH ROW EXECUTE FUNCTION prevent_manual_adjustment_event_mutation();

CREATE OR REPLACE FUNCTION prevent_terminal_adjustment_mutation() RETURNS trigger AS $$
BEGIN
    IF OLD.status IN ('EXECUTED','CANCELLED','REJECTED') AND
       (NEW.adjustment_reference<>OLD.adjustment_reference OR NEW.business_date<>OLD.business_date OR
        NEW.currency<>OLD.currency OR NEW.reason_code<>OLD.reason_code OR NEW.reason_detail<>OLD.reason_detail OR
        NEW.requested_by<>OLD.requested_by OR NEW.evidence_hash<>OLD.evidence_hash) THEN
        RAISE EXCEPTION 'terminal adjustment identity/evidence is immutable';
    END IF;
    RETURN NEW;
END; $$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_manual_adjustment_terminal_immutable ON manual_financial_adjustment;
CREATE TRIGGER trg_manual_adjustment_terminal_immutable BEFORE UPDATE ON manual_financial_adjustment
FOR EACH ROW EXECUTE FUNCTION prevent_terminal_adjustment_mutation();
