CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE SCHEMA IF NOT EXISTS object_storage;

CREATE TABLE IF NOT EXISTS object_storage.objects (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    storage_bucket      VARCHAR(100) NOT NULL,
    object_key          VARCHAR(500) NOT NULL,
    object_size_bytes   BIGINT,
    content_type        VARCHAR(80) NOT NULL DEFAULT 'application/octet-stream',
    content_encoding    VARCHAR(20),
    checksum_algorithm  VARCHAR(20) NOT NULL DEFAULT 'SHA-256',
    payload_hash        VARCHAR(128),
    encryption_key_id   VARCHAR(64),
    object_purpose      VARCHAR(80) NOT NULL,
    source_table        VARCHAR(100),
    reference_id        VARCHAR(80),
    business_date       DATE,
    retention_until     DATE,
    created_at          TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_object_storage_objects_key UNIQUE (storage_bucket, object_key)
);

CREATE TABLE IF NOT EXISTS object_storage.retention_policies (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    policy_name           VARCHAR(100) NOT NULL UNIQUE,
    bucket_name           VARCHAR(100) NOT NULL,
    prefix_pattern        VARCHAR(300) NOT NULL,
    retention_years       INT NOT NULL,
    compression_required  BOOLEAN NOT NULL DEFAULT TRUE,
    encryption_required   BOOLEAN NOT NULL DEFAULT TRUE,
    checksum_required     BOOLEAN NOT NULL DEFAULT TRUE,
    active                BOOLEAN NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP(3) NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS object_storage.manifests (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    archive_job_id      BIGINT,
    manifest_object_id  BIGINT,
    manifest_key        VARCHAR(500),
    source_table        VARCHAR(100) NOT NULL,
    row_count           BIGINT NOT NULL DEFAULT 0,
    size_bytes          BIGINT,
    checksum_algorithm  VARCHAR(20) NOT NULL DEFAULT 'SHA-256',
    checksum            VARCHAR(128),
    verified_at         TIMESTAMP(3),
    created_at          TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_object_storage_manifests_object
        FOREIGN KEY (manifest_object_id) REFERENCES object_storage.objects(id)
);

INSERT INTO object_storage.retention_policies (
    policy_name,
    bucket_name,
    prefix_pattern,
    retention_years
) VALUES (
    'switching-archive-default',
    'switching-archive',
    '*',
    10
) ON CONFLICT (policy_name) DO NOTHING;

CREATE TABLE IF NOT EXISTS payment_flows_archive (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    original_id            BIGINT NOT NULL,
    flow_ref               VARCHAR(80) NOT NULL,
    source_bank            VARCHAR(32) NOT NULL,
    destination_bank       VARCHAR(32) NOT NULL,
    amount                 NUMERIC(18,2) NOT NULL,
    currency               VARCHAR(8) NOT NULL,
    status                 VARCHAR(30) NOT NULL,
    original_business_date DATE NOT NULL,
    object_id              BIGINT REFERENCES object_storage.objects(id),
    archived_at            TIMESTAMP(3) NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS inquiries_archive (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    original_id            BIGINT NOT NULL,
    inquiry_ref            VARCHAR(80) NOT NULL,
    flow_ref               VARCHAR(80),
    source_bank            VARCHAR(32) NOT NULL,
    destination_bank       VARCHAR(32) NOT NULL,
    creditor_account       VARCHAR(34) NOT NULL,
    status                 VARCHAR(30) NOT NULL,
    amount                 NUMERIC(18,2),
    currency               VARCHAR(8),
    original_business_date DATE NOT NULL,
    object_id              BIGINT REFERENCES object_storage.objects(id),
    archived_at            TIMESTAMP(3) NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS transactions_archive (
    id                         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    original_id                BIGINT NOT NULL,
    transaction_ref            VARCHAR(80) NOT NULL,
    inquiry_ref                VARCHAR(80),
    flow_ref                   VARCHAR(80),
    source_bank                VARCHAR(32) NOT NULL,
    destination_bank           VARCHAR(32) NOT NULL,
    source_account_no          VARCHAR(34) NOT NULL,
    destination_account_no     VARCHAR(34) NOT NULL,
    amount                     NUMERIC(18,2) NOT NULL,
    currency                   VARCHAR(8) NOT NULL,
    status                     VARCHAR(30) NOT NULL,
    original_business_date     DATE NOT NULL,
    object_id                  BIGINT REFERENCES object_storage.objects(id),
    archived_at                TIMESTAMP(3) NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS transaction_status_history_archive (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    original_id            BIGINT NOT NULL,
    transaction_ref        VARCHAR(80) NOT NULL,
    from_status            VARCHAR(30),
    to_status              VARCHAR(30) NOT NULL,
    reason_code            VARCHAR(50),
    actor                  VARCHAR(100),
    original_business_date DATE NOT NULL,
    occurred_at            TIMESTAMP(3) NOT NULL,
    archived_at            TIMESTAMP(3) NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS transaction_events_archive (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    original_id            BIGINT NOT NULL,
    transaction_ref        VARCHAR(80) NOT NULL,
    event_type             VARCHAR(60) NOT NULL,
    original_business_date DATE NOT NULL,
    occurred_at            TIMESTAMP(3) NOT NULL,
    archived_at            TIMESTAMP(3) NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS iso_messages_archive (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    original_id            BIGINT NOT NULL,
    transaction_ref        VARCHAR(80),
    inquiry_ref            VARCHAR(80),
    flow_ref               VARCHAR(80),
    participant            VARCHAR(32),
    message_type           VARCHAR(50) NOT NULL,
    direction              VARCHAR(10) NOT NULL,
    status                 VARCHAR(30),
    original_business_date DATE NOT NULL,
    object_id              BIGINT REFERENCES object_storage.objects(id),
    archived_at            TIMESTAMP(3) NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS iso_validation_errors_archive (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    original_id            BIGINT NOT NULL,
    iso_message_id         BIGINT NOT NULL,
    field_path             VARCHAR(200),
    error_code             VARCHAR(50) NOT NULL,
    error_message          TEXT,
    severity               VARCHAR(10) NOT NULL,
    original_business_date DATE NOT NULL,
    created_at             TIMESTAMP(3) NOT NULL,
    archived_at            TIMESTAMP(3) NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS settlement_items_archive (
    id                       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    original_id              BIGINT NOT NULL,
    cycle_id                 BIGINT NOT NULL,
    bank_code                VARCHAR(32) NOT NULL,
    transaction_ref          VARCHAR(80) NOT NULL,
    direction                VARCHAR(10) NOT NULL,
    amount                   NUMERIC(18,2) NOT NULL,
    currency                 VARCHAR(8) NOT NULL,
    original_settlement_date DATE NOT NULL,
    archived_at              TIMESTAMP(3) NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS reconciliation_items_archive (
    id                           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    original_id                  BIGINT NOT NULL,
    file_id                      BIGINT NOT NULL,
    transaction_ref              VARCHAR(80),
    amount                       NUMERIC(18,2),
    currency                     VARCHAR(8),
    match_status                 VARCHAR(20) NOT NULL,
    original_reconciliation_date DATE NOT NULL,
    object_id                    BIGINT REFERENCES object_storage.objects(id),
    archived_at                  TIMESTAMP(3) NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS connector_call_logs_archive (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    original_id            BIGINT,
    transaction_ref        VARCHAR(80),
    inquiry_ref            VARCHAR(80),
    flow_ref               VARCHAR(80),
    connector_name         VARCHAR(128) NOT NULL,
    participant            VARCHAR(32),
    direction              VARCHAR(10) NOT NULL,
    status                 VARCHAR(30) NOT NULL,
    http_status            INT,
    duration_ms            INT,
    original_business_date DATE NOT NULL,
    object_id              BIGINT REFERENCES object_storage.objects(id),
    archived_at            TIMESTAMP(3) NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS arc_payment_flows_ref ON payment_flows_archive(flow_ref);
CREATE INDEX IF NOT EXISTS arc_inquiries_ref ON inquiries_archive(inquiry_ref);
CREATE INDEX IF NOT EXISTS arc_transactions_ref ON transactions_archive(transaction_ref);
CREATE INDEX IF NOT EXISTS arc_iso_messages_txn_ref ON iso_messages_archive(transaction_ref);
CREATE INDEX IF NOT EXISTS arc_iso_validation_errors_msg_id ON iso_validation_errors_archive(iso_message_id);
CREATE INDEX IF NOT EXISTS arc_iso_validation_errors_business_date ON iso_validation_errors_archive(original_business_date);
CREATE INDEX IF NOT EXISTS arc_connector_call_logs_ref ON connector_call_logs_archive(transaction_ref, inquiry_ref);
CREATE INDEX IF NOT EXISTS arc_payment_flows_object_id ON payment_flows_archive(object_id);
CREATE INDEX IF NOT EXISTS arc_inquiries_object_id ON inquiries_archive(object_id);
CREATE INDEX IF NOT EXISTS arc_transactions_object_id ON transactions_archive(object_id);
CREATE INDEX IF NOT EXISTS arc_iso_messages_object_id ON iso_messages_archive(object_id);
CREATE INDEX IF NOT EXISTS arc_reconciliation_items_object_id ON reconciliation_items_archive(object_id);
CREATE INDEX IF NOT EXISTS arc_connector_call_logs_object_id ON connector_call_logs_archive(object_id);
CREATE INDEX IF NOT EXISTS obj_objects_reference ON object_storage.objects(reference_id);
CREATE INDEX IF NOT EXISTS obj_objects_business_date ON object_storage.objects(business_date);
CREATE INDEX IF NOT EXISTS obj_objects_purpose_date ON object_storage.objects(object_purpose, business_date);
CREATE INDEX IF NOT EXISTS obj_manifests_archive_job ON object_storage.manifests(archive_job_id);
CREATE INDEX IF NOT EXISTS obj_manifests_source_table ON object_storage.manifests(source_table);
