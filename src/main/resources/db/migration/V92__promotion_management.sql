-- Phase II-B: discrete promotion management and settlement ledger.
CREATE TABLE promotion (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    promotion_type VARCHAR(24) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    priority INTEGER NOT NULL DEFAULT 100,
    combinable BOOLEAN NOT NULL DEFAULT false,
    funder_participant_id VARCHAR(64) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    budget_cap NUMERIC(19,4) NOT NULL,
    budget_reserved NUMERIC(19,4) NOT NULL DEFAULT 0,
    budget_consumed NUMERIC(19,4) NOT NULL DEFAULT 0,
    discount_value NUMERIC(19,4) NOT NULL,
    discount_mode VARCHAR(16) NOT NULL DEFAULT 'FIXED',
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(128) NOT NULL,
    suspended_by VARCHAR(128),
    suspended_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_promotion_type CHECK (promotion_type IN ('WAIVER','CASHBACK','SPONSORED')),
    CONSTRAINT ck_promotion_status CHECK (status IN ('DRAFT','ACTIVE','SUSPENDED','EXPIRED','CLOSED')),
    CONSTRAINT ck_promotion_discount_mode CHECK (discount_mode IN ('FIXED','PERCENT')),
    CONSTRAINT ck_promotion_window CHECK (ends_at > starts_at),
    CONSTRAINT ck_promotion_budget CHECK (
        budget_cap >= 0 AND budget_reserved >= 0 AND budget_consumed >= 0
        AND budget_reserved + budget_consumed <= budget_cap
    ),
    CONSTRAINT ck_promotion_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_promotion_discount CHECK (
        discount_value >= 0
        AND (discount_mode <> 'PERCENT' OR discount_value <= 100)
    )
);
CREATE INDEX ix_promotion_active ON promotion(status, starts_at, ends_at, priority, created_at);
CREATE TRIGGER trg_promotion_updated_at BEFORE UPDATE ON promotion
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE promotion_eligibility_rule (
    id UUID PRIMARY KEY,
    promotion_id UUID NOT NULL REFERENCES promotion(id) ON DELETE CASCADE,
    rule_order INTEGER NOT NULL,
    rule_json JSONB NOT NULL,
    rule_sha256 VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_promotion_rule_order UNIQUE (promotion_id, rule_order),
    CONSTRAINT ck_promotion_rule_sha CHECK (rule_sha256 ~ '^[0-9a-f]{64}$')
);

CREATE TABLE promotion_application (
    id UUID PRIMARY KEY,
    promotion_id UUID NOT NULL REFERENCES promotion(id) ON DELETE RESTRICT,
    transaction_reference VARCHAR(128) NOT NULL,
    participant_id VARCHAR(64) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    gross_fee NUMERIC(19,4) NOT NULL,
    discount_amount NUMERIC(19,4) NOT NULL,
    net_fee NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'RESERVED',
    eligibility_evidence JSONB NOT NULL,
    evidence_sha256 VARCHAR(64) NOT NULL,
    reserved_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    consumed_at TIMESTAMPTZ,
    released_at TIMESTAMPTZ,
    CONSTRAINT uq_promotion_application_tx UNIQUE (promotion_id, transaction_reference),
    CONSTRAINT ck_promotion_application_amounts CHECK (
        gross_fee >= 0 AND discount_amount >= 0 AND net_fee >= 0
        AND discount_amount <= gross_fee AND net_fee = gross_fee - discount_amount
    ),
    CONSTRAINT ck_promotion_application_status CHECK (status IN ('RESERVED','CONSUMED','RELEASED','FAILED')),
    CONSTRAINT ck_promotion_application_sha CHECK (evidence_sha256 ~ '^[0-9a-f]{64}$')
);
CREATE INDEX ix_promotion_application_tx ON promotion_application(transaction_reference);

CREATE TABLE promotion_settlement (
    id UUID PRIMARY KEY,
    promotion_application_id UUID NOT NULL UNIQUE REFERENCES promotion_application(id) ON DELETE RESTRICT,
    funder_participant_id VARCHAR(64) NOT NULL,
    beneficiary_participant_id VARCHAR(64) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    settlement_reference VARCHAR(128) NOT NULL UNIQUE,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    settled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_promotion_settlement_amount CHECK (amount > 0),
    CONSTRAINT ck_promotion_settlement_status CHECK (status IN ('PENDING','SETTLED','FAILED','REVERSED'))
);
