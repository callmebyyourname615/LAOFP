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

INSERT INTO object_storage.objects (
    storage_bucket,
    object_key,
    object_size_bytes,
    content_type,
    content_encoding,
    payload_hash,
    encryption_key_id,
    object_purpose,
    source_table,
    reference_id,
    business_date
)
SELECT
    storage_bucket,
    object_key,
    object_size_bytes,
    content_type,
    content_encoding,
    payload_hash,
    encryption_key_id,
    'ARCHIVE_PAYLOAD',
    table_name,
    reference_id,
    business_date
FROM archive_object_index
ON CONFLICT (storage_bucket, object_key) DO NOTHING;

INSERT INTO object_storage.manifests (
    archive_job_id,
    manifest_object_id,
    manifest_key,
    source_table,
    row_count,
    size_bytes,
    checksum
)
SELECT
    am.archive_job_id,
    obj.id,
    am.manifest_key,
    am.table_name,
    am.row_count,
    am.size_bytes,
    am.checksum
FROM archive_manifests am
LEFT JOIN object_storage.objects obj
    ON obj.storage_bucket = 'switching-archive'
   AND obj.object_key = am.manifest_key
ON CONFLICT DO NOTHING;

ALTER TABLE payment_flows_archive ADD COLUMN IF NOT EXISTS object_id BIGINT;
ALTER TABLE inquiries_archive ADD COLUMN IF NOT EXISTS object_id BIGINT;
ALTER TABLE transactions_archive ADD COLUMN IF NOT EXISTS object_id BIGINT;
ALTER TABLE iso_messages_archive ADD COLUMN IF NOT EXISTS object_id BIGINT;
ALTER TABLE reconciliation_items_archive ADD COLUMN IF NOT EXISTS object_id BIGINT;
ALTER TABLE connector_call_logs_archive ADD COLUMN IF NOT EXISTS object_id BIGINT;

UPDATE payment_flows_archive a
SET object_id = obj.id
FROM object_storage.objects obj
WHERE a.cold_storage_key IS NOT NULL
  AND obj.storage_bucket = 'switching-archive'
  AND obj.object_key = a.cold_storage_key;

UPDATE inquiries_archive a
SET object_id = obj.id
FROM object_storage.objects obj
WHERE a.cold_storage_key IS NOT NULL
  AND obj.storage_bucket = 'switching-archive'
  AND obj.object_key = a.cold_storage_key;

UPDATE transactions_archive a
SET object_id = obj.id
FROM object_storage.objects obj
WHERE a.cold_storage_key IS NOT NULL
  AND obj.storage_bucket = 'switching-archive'
  AND obj.object_key = a.cold_storage_key;

UPDATE iso_messages_archive a
SET object_id = obj.id
FROM object_storage.objects obj
WHERE a.cold_storage_key IS NOT NULL
  AND obj.storage_bucket = 'switching-archive'
  AND obj.object_key = a.cold_storage_key;

UPDATE connector_call_logs_archive a
SET object_id = obj.id
FROM object_storage.objects obj
WHERE a.payload_location IS NOT NULL
  AND obj.storage_bucket = 'switching-archive'
  AND obj.object_key = a.payload_location;

ALTER TABLE payment_flows_archive
    ADD CONSTRAINT fk_payment_flows_archive_object
        FOREIGN KEY (object_id) REFERENCES object_storage.objects(id);

ALTER TABLE inquiries_archive
    ADD CONSTRAINT fk_inquiries_archive_object
        FOREIGN KEY (object_id) REFERENCES object_storage.objects(id);

ALTER TABLE transactions_archive
    ADD CONSTRAINT fk_transactions_archive_object
        FOREIGN KEY (object_id) REFERENCES object_storage.objects(id);

ALTER TABLE iso_messages_archive
    ADD CONSTRAINT fk_iso_messages_archive_object
        FOREIGN KEY (object_id) REFERENCES object_storage.objects(id);

ALTER TABLE reconciliation_items_archive
    ADD CONSTRAINT fk_reconciliation_items_archive_object
        FOREIGN KEY (object_id) REFERENCES object_storage.objects(id);

ALTER TABLE connector_call_logs_archive
    ADD CONSTRAINT fk_connector_call_logs_archive_object
        FOREIGN KEY (object_id) REFERENCES object_storage.objects(id);

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

ALTER TABLE payment_flows_archive
    DROP COLUMN IF EXISTS cold_storage_key;

ALTER TABLE inquiries_archive
    DROP COLUMN IF EXISTS cold_storage_key,
    DROP COLUMN IF EXISTS payload_hash,
    DROP COLUMN IF EXISTS payload_size_bytes,
    DROP COLUMN IF EXISTS encryption_key_id;

ALTER TABLE transactions_archive
    DROP COLUMN IF EXISTS cold_storage_key,
    DROP COLUMN IF EXISTS payload_hash,
    DROP COLUMN IF EXISTS payload_size_bytes,
    DROP COLUMN IF EXISTS encryption_key_id;

ALTER TABLE iso_messages_archive
    DROP COLUMN IF EXISTS cold_storage_key,
    DROP COLUMN IF EXISTS payload_hash,
    DROP COLUMN IF EXISTS payload_size_bytes,
    DROP COLUMN IF EXISTS encryption_key_id;

ALTER TABLE reconciliation_items_archive
    DROP COLUMN IF EXISTS cold_storage_key,
    DROP COLUMN IF EXISTS payload_hash,
    DROP COLUMN IF EXISTS payload_size_bytes,
    DROP COLUMN IF EXISTS encryption_key_id;

ALTER TABLE connector_call_logs_archive
    DROP COLUMN IF EXISTS payload_location,
    DROP COLUMN IF EXISTS payload_hash,
    DROP COLUMN IF EXISTS payload_size_bytes,
    DROP COLUMN IF EXISTS encryption_key_id;

DROP TABLE archive_manifests;
DROP TABLE archive_object_index;
