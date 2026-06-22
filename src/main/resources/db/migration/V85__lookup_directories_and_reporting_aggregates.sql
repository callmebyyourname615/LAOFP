-- Database-hardening: durable global reference directories and replica-safe reporting aggregates.

CREATE SCHEMA IF NOT EXISTS reporting;

-- The partitioned parents cannot enforce a global unique transaction/inquiry reference.
-- These compact directories are the authoritative cross-date lookup index and are
-- maintained in the same database transaction as their source row.
CREATE OR REPLACE FUNCTION sync_transaction_lookup() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        DELETE FROM transaction_lookup
         WHERE transaction_ref = OLD.transaction_ref
           AND business_date = OLD.business_date;
        RETURN OLD;
    END IF;

    INSERT INTO transaction_lookup (
        transaction_ref, flow_ref, inquiry_ref, source_bank, destination_bank,
        amount, currency, status, business_date, created_at, updated_at
    ) VALUES (
        NEW.transaction_ref, NEW.flow_ref, NEW.inquiry_ref, NEW.source_bank, NEW.destination_bank,
        NEW.amount, NEW.currency, NEW.status, NEW.business_date, NEW.created_at, NEW.updated_at
    ) ON CONFLICT (transaction_ref) DO UPDATE SET
        flow_ref = EXCLUDED.flow_ref,
        inquiry_ref = EXCLUDED.inquiry_ref,
        source_bank = EXCLUDED.source_bank,
        destination_bank = EXCLUDED.destination_bank,
        amount = EXCLUDED.amount,
        currency = EXCLUDED.currency,
        status = EXCLUDED.status,
        business_date = EXCLUDED.business_date,
        updated_at = COALESCE(EXCLUDED.updated_at, NOW());
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sync_transaction_lookup ON transactions;
CREATE TRIGGER trg_sync_transaction_lookup
AFTER INSERT OR UPDATE OR DELETE ON transactions
FOR EACH ROW EXECUTE FUNCTION sync_transaction_lookup();

CREATE OR REPLACE FUNCTION sync_inquiry_lookup() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        DELETE FROM inquiry_lookup
         WHERE inquiry_ref = OLD.inquiry_ref
           AND business_date = OLD.business_date;
        RETURN OLD;
    END IF;

    INSERT INTO inquiry_lookup (
        inquiry_ref, flow_ref, source_bank, destination_bank, creditor_account,
        status, business_date, created_at, updated_at
    ) VALUES (
        NEW.inquiry_ref, NEW.flow_ref, NEW.source_bank, NEW.destination_bank, NEW.creditor_account,
        NEW.status, NEW.business_date, NEW.created_at, NEW.updated_at
    ) ON CONFLICT (inquiry_ref) DO UPDATE SET
        flow_ref = EXCLUDED.flow_ref,
        source_bank = EXCLUDED.source_bank,
        destination_bank = EXCLUDED.destination_bank,
        creditor_account = EXCLUDED.creditor_account,
        status = EXCLUDED.status,
        business_date = EXCLUDED.business_date,
        updated_at = COALESCE(EXCLUDED.updated_at, NOW());
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sync_inquiry_lookup ON inquiries;
CREATE TRIGGER trg_sync_inquiry_lookup
AFTER INSERT OR UPDATE OR DELETE ON inquiries
FOR EACH ROW EXECUTE FUNCTION sync_inquiry_lookup();

-- Backfill pre-existing rows before application traffic begins using the triggers.
INSERT INTO transaction_lookup (
    transaction_ref, flow_ref, inquiry_ref, source_bank, destination_bank,
    amount, currency, status, business_date, created_at, updated_at
)
SELECT transaction_ref, flow_ref, inquiry_ref, source_bank, destination_bank,
       amount, currency, status, business_date, created_at, updated_at
  FROM transactions
ON CONFLICT (transaction_ref) DO UPDATE SET
    flow_ref = EXCLUDED.flow_ref, inquiry_ref = EXCLUDED.inquiry_ref,
    source_bank = EXCLUDED.source_bank, destination_bank = EXCLUDED.destination_bank,
    amount = EXCLUDED.amount, currency = EXCLUDED.currency, status = EXCLUDED.status,
    business_date = EXCLUDED.business_date, updated_at = COALESCE(EXCLUDED.updated_at, NOW());

INSERT INTO inquiry_lookup (
    inquiry_ref, flow_ref, source_bank, destination_bank, creditor_account,
    status, business_date, created_at, updated_at
)
SELECT inquiry_ref, flow_ref, source_bank, destination_bank, creditor_account,
       status, business_date, created_at, updated_at
  FROM inquiries
ON CONFLICT (inquiry_ref) DO UPDATE SET
    flow_ref = EXCLUDED.flow_ref, source_bank = EXCLUDED.source_bank,
    destination_bank = EXCLUDED.destination_bank, creditor_account = EXCLUDED.creditor_account,
    status = EXCLUDED.status, business_date = EXCLUDED.business_date,
    updated_at = COALESCE(EXCLUDED.updated_at, NOW());

CREATE TABLE reporting.transaction_status_daily (
    summary_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL,
    total_count BIGINT NOT NULL,
    calculated_at TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    source_through_at TIMESTAMP(3) NOT NULL,
    source_row_count BIGINT NOT NULL,
    aggregation_version VARCHAR(30) NOT NULL DEFAULT 'v1',
    PRIMARY KEY (summary_date, status)
);

CREATE TABLE reporting.inquiry_status_daily (
    summary_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL,
    total_count BIGINT NOT NULL,
    calculated_at TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    source_through_at TIMESTAMP(3) NOT NULL,
    source_row_count BIGINT NOT NULL,
    aggregation_version VARCHAR(30) NOT NULL DEFAULT 'v1',
    PRIMARY KEY (summary_date, status)
);

CREATE TABLE reporting.outbox_status_daily (
    summary_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL,
    total_count BIGINT NOT NULL,
    calculated_at TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    source_through_at TIMESTAMP(3) NOT NULL,
    source_row_count BIGINT NOT NULL,
    aggregation_version VARCHAR(30) NOT NULL DEFAULT 'v1',
    PRIMARY KEY (summary_date, status)
);

CREATE TABLE reporting.refresh_state (
    dataset VARCHAR(80) PRIMARY KEY,
    refreshed_at TIMESTAMP(3) NOT NULL,
    source_through_at TIMESTAMP(3) NOT NULL,
    source_row_count BIGINT NOT NULL,
    aggregation_version VARCHAR(30) NOT NULL
);

INSERT INTO reporting.transaction_status_daily
    (summary_date, status, total_count, calculated_at, source_through_at, source_row_count)
SELECT business_date, status, COUNT(*), NOW(), COALESCE(MAX(updated_at), MAX(created_at), NOW()), COUNT(*)
  FROM transactions
 GROUP BY business_date, status;

INSERT INTO reporting.inquiry_status_daily
    (summary_date, status, total_count, calculated_at, source_through_at, source_row_count)
SELECT business_date, status, COUNT(*), NOW(), COALESCE(MAX(updated_at), MAX(created_at), NOW()), COUNT(*)
  FROM inquiries
 GROUP BY business_date, status;

INSERT INTO reporting.outbox_status_daily
    (summary_date, status, total_count, calculated_at, source_through_at, source_row_count)
SELECT created_at::date, status, COUNT(*), NOW(), COALESCE(MAX(updated_at), MAX(created_at), NOW()), COUNT(*)
  FROM outbox_messages
 GROUP BY created_at::date, status;

-- Indexes align the hot operational list filters with date partition pruning.
CREATE INDEX IF NOT EXISTS idx_transactions_date_status_created
    ON transactions (business_date, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_date_source_status
    ON transactions (business_date, source_bank, status);
CREATE INDEX IF NOT EXISTS idx_transactions_date_destination_status
    ON transactions (business_date, destination_bank, status);
CREATE INDEX IF NOT EXISTS idx_inquiries_date_status_created
    ON inquiries (business_date, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_outbox_messages_created_status
    ON outbox_messages (created_at DESC, status);
