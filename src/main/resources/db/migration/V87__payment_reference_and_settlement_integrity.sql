-- Payment identity must be globally unique even though the source tables are
-- date partitioned.  The lookup directories provide that global constraint.

CREATE OR REPLACE FUNCTION sync_transaction_lookup() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        DELETE FROM transaction_lookup
         WHERE transaction_ref = OLD.transaction_ref AND business_date = OLD.business_date;
        RETURN OLD;
    END IF;
    IF TG_OP = 'UPDATE' AND NEW.transaction_ref IS DISTINCT FROM OLD.transaction_ref THEN
        RAISE EXCEPTION 'transaction_ref is immutable';
    END IF;
    IF TG_OP = 'INSERT' THEN
        INSERT INTO transaction_lookup (transaction_ref, flow_ref, inquiry_ref, source_bank, destination_bank,
            amount, currency, status, business_date, created_at, updated_at)
        VALUES (NEW.transaction_ref, NEW.flow_ref, NEW.inquiry_ref, NEW.source_bank, NEW.destination_bank,
            NEW.amount, NEW.currency, NEW.status, NEW.business_date, NEW.created_at, NEW.updated_at);
    ELSE
        UPDATE transaction_lookup SET flow_ref=NEW.flow_ref, inquiry_ref=NEW.inquiry_ref,
            source_bank=NEW.source_bank, destination_bank=NEW.destination_bank, amount=NEW.amount,
            currency=NEW.currency, status=NEW.status, business_date=NEW.business_date,
            updated_at=COALESCE(NEW.updated_at, NOW())
        WHERE transaction_ref=NEW.transaction_ref;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sync_inquiry_lookup() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        DELETE FROM inquiry_lookup WHERE inquiry_ref=OLD.inquiry_ref AND business_date=OLD.business_date;
        RETURN OLD;
    END IF;
    IF TG_OP = 'UPDATE' AND NEW.inquiry_ref IS DISTINCT FROM OLD.inquiry_ref THEN
        RAISE EXCEPTION 'inquiry_ref is immutable';
    END IF;
    IF TG_OP = 'INSERT' THEN
        INSERT INTO inquiry_lookup (inquiry_ref, flow_ref, source_bank, destination_bank, creditor_account,
            status, business_date, created_at, updated_at)
        VALUES (NEW.inquiry_ref, NEW.flow_ref, NEW.source_bank, NEW.destination_bank, NEW.creditor_account,
            NEW.status, NEW.business_date, NEW.created_at, NEW.updated_at);
    ELSE
        UPDATE inquiry_lookup SET flow_ref=NEW.flow_ref, source_bank=NEW.source_bank,
            destination_bank=NEW.destination_bank, creditor_account=NEW.creditor_account, status=NEW.status,
            business_date=NEW.business_date, updated_at=COALESCE(NEW.updated_at, NOW())
        WHERE inquiry_ref=NEW.inquiry_ref;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- One debit/credit settlement leg may be recorded only once in a settlement cycle.
CREATE UNIQUE INDEX uq_settlement_items_cycle_transaction_leg
    ON settlement_items (cycle_id, transaction_ref, bank_code, direction, settlement_date);

-- Basic financial invariants on the hot payment path.  NOT VALID avoids a long
-- blocking historical scan during rollout; validate after the data-quality job passes.
ALTER TABLE transactions ADD CONSTRAINT ck_transactions_positive_amount
    CHECK (amount > 0) NOT VALID;
ALTER TABLE settlement_items ADD CONSTRAINT ck_settlement_items_positive_amount
    CHECK (amount > 0) NOT VALID;
ALTER TABLE outbox_messages ADD CONSTRAINT ck_outbox_retry_counts
    CHECK (retry_count >= 0 AND max_retries > 0) NOT VALID;
