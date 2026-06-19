-- ============================================================
-- V5 · ISO 20022 tables (partitioned by business_date)
--
--   iso_messages         — message metadata (PACS.008 / PACS.002 / CAMT.056)
--   iso_message_payloads — raw encrypted/plain content (split from metadata)
--   iso_validation_errors — field-level validation failures
-- ============================================================

-- ── iso_messages ────────────────────────────────────────────
CREATE TABLE iso_messages (
    id               BIGINT       GENERATED ALWAYS AS IDENTITY,
    correlation_ref  VARCHAR(100) NULL,   -- groups request+response pair
    transaction_ref  VARCHAR(80)  NULL,
    inquiry_ref      VARCHAR(80)  NULL,
    message_id       VARCHAR(100) NULL,   -- MsgId in the ISO XML
    end_to_end_id    VARCHAR(100) NULL,
    instruction_id   VARCHAR(100) NULL,
    message_type     VARCHAR(50)  NOT NULL,   -- PACS_008 | PACS_002 | CAMT_027 | CAMT_056 | CAMT_029
    direction        VARCHAR(10)  NOT NULL,   -- OUTBOUND | INBOUND
    source_bank      VARCHAR(32)  NULL,
    destination_bank VARCHAR(32)  NULL,
    connector_name   VARCHAR(128) NULL,
    security_status  VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    -- PENDING | ENCRYPTED | SIGNED | VERIFIED | REJECTED
    validation_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    -- PENDING | VALID | INVALID | SKIPPED
    error_code       VARCHAR(50)  NULL,
    error_message    VARCHAR(500) NULL,
    business_date    DATE         NOT NULL DEFAULT CURRENT_DATE,
    created_at       TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP(3) NULL,
    PRIMARY KEY (id, business_date)
) PARTITION BY RANGE (business_date);

CREATE INDEX idx_iso_messages_txn_ref
    ON iso_messages(transaction_ref, business_date) WHERE transaction_ref IS NOT NULL;
CREATE INDEX idx_iso_messages_inquiry_ref
    ON iso_messages(inquiry_ref, business_date) WHERE inquiry_ref IS NOT NULL;
CREATE INDEX idx_iso_messages_correlation
    ON iso_messages(correlation_ref, business_date) WHERE correlation_ref IS NOT NULL;
CREATE INDEX idx_iso_messages_message_id
    ON iso_messages(message_id, business_date) WHERE message_id IS NOT NULL;
CREATE INDEX idx_iso_messages_type_date
    ON iso_messages(message_type, business_date);

CREATE TRIGGER trg_iso_messages_updated_at
    BEFORE UPDATE ON iso_messages
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── iso_message_payloads ─────────────────────────────────────
-- Raw message content stored separately from metadata to keep
-- iso_messages rows small for fast indexed lookups.
CREATE TABLE iso_message_payloads (
    id                  BIGINT       GENERATED ALWAYS AS IDENTITY,
    iso_message_id      BIGINT       NOT NULL,
    payload_type        VARCHAR(20)  NOT NULL DEFAULT 'REQUEST',   -- REQUEST | RESPONSE
    plain_payload       TEXT         NULL,
    encrypted_payload   TEXT         NULL,
    payload_size_bytes  INT          NULL,
    payload_hash        VARCHAR(64)  NULL,   -- SHA-256 of the plaintext
    stored_in_cold      BOOLEAN      NOT NULL DEFAULT FALSE,
    cold_storage_key    VARCHAR(500) NULL,   -- object storage key if archived
    business_date       DATE         NOT NULL DEFAULT CURRENT_DATE,
    created_at          TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, business_date)
) PARTITION BY RANGE (business_date);

CREATE INDEX idx_iso_payloads_msg_id
    ON iso_message_payloads(iso_message_id, business_date);

-- ── iso_validation_errors ────────────────────────────────────
CREATE TABLE iso_validation_errors (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY,
    iso_message_id  BIGINT       NOT NULL,
    field_path      VARCHAR(200) NULL,   -- XPath or JSON path of the invalid field
    error_code      VARCHAR(50)  NOT NULL,
    error_message   TEXT         NULL,
    severity        VARCHAR(10)  NOT NULL DEFAULT 'ERROR',   -- ERROR | WARNING
    business_date   DATE         NOT NULL DEFAULT CURRENT_DATE,
    created_at      TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, business_date)
) PARTITION BY RANGE (business_date);

CREATE INDEX idx_iso_validation_errors_msg_id
    ON iso_validation_errors(iso_message_id, business_date);

-- ── Pre-create partitions (-7 to +90 days) ──────────────────
DO $$
DECLARE
    target_date DATE;
    next_date   DATE;
    pname       TEXT;
BEGIN
    FOR d IN -7..90 LOOP
        target_date := CURRENT_DATE + d;
        next_date   := target_date + 1;

        pname := 'iso_messages_' || TO_CHAR(target_date, 'YYYYMMDD');
        EXECUTE FORMAT(
            'CREATE TABLE IF NOT EXISTS %I PARTITION OF iso_messages
             FOR VALUES FROM (%L) TO (%L)',
            pname, target_date::TEXT, next_date::TEXT);

        pname := 'iso_message_payloads_' || TO_CHAR(target_date, 'YYYYMMDD');
        EXECUTE FORMAT(
            'CREATE TABLE IF NOT EXISTS %I PARTITION OF iso_message_payloads
             FOR VALUES FROM (%L) TO (%L)',
            pname, target_date::TEXT, next_date::TEXT);

        pname := 'iso_validation_errors_' || TO_CHAR(target_date, 'YYYYMMDD');
        EXECUTE FORMAT(
            'CREATE TABLE IF NOT EXISTS %I PARTITION OF iso_validation_errors
             FOR VALUES FROM (%L) TO (%L)',
            pname, target_date::TEXT, next_date::TEXT);

    END LOOP;
END;
$$;
