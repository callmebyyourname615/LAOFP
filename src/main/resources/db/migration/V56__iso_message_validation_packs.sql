CREATE TABLE IF NOT EXISTS iso_validation_pack (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pack_code VARCHAR(80) NOT NULL UNIQUE,
    message_type VARCHAR(40) NOT NULL,
    version VARCHAR(40) NOT NULL,
    schema_uri TEXT NOT NULL,
    schema_sha256 CHAR(64) NOT NULL,
    rule_uri TEXT,
    rule_sha256 CHAR(64),
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    activated_at TIMESTAMPTZ,
    retired_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS iso_validation_result (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_reference VARCHAR(120) NOT NULL,
    pack_code VARCHAR(80) NOT NULL REFERENCES iso_validation_pack(pack_code),
    result VARCHAR(24) NOT NULL,
    errors JSONB NOT NULL DEFAULT '[]'::jsonb,
    canonical_payload_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(transaction_reference, pack_code)
);
