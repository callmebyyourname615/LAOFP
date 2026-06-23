-- Phase 62H: concurrency-safe promotion budget reservation and funder ledger.
-- These tables complement the Phase II promotion model without executing rules.

CREATE TABLE IF NOT EXISTS promotion_budget_account (
    promotion_id BIGINT PRIMARY KEY,
    currency VARCHAR(8) NOT NULL,
    budget_cap NUMERIC(24,4) NOT NULL CHECK (budget_cap >= 0),
    reserved_amount NUMERIC(24,4) NOT NULL DEFAULT 0 CHECK (reserved_amount >= 0),
    consumed_amount NUMERIC(24,4) NOT NULL DEFAULT 0 CHECK (consumed_amount >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_promotion_budget_total CHECK (reserved_amount + consumed_amount <= budget_cap)
);

CREATE TABLE IF NOT EXISTS promotion_budget_reservation (
    reservation_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    promotion_id BIGINT NOT NULL REFERENCES promotion_budget_account(promotion_id),
    transaction_ref VARCHAR(120) NOT NULL,
    amount NUMERIC(24,4) NOT NULL CHECK (amount > 0),
    currency VARCHAR(8) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'RESERVED',
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    released_at TIMESTAMPTZ,
    refunded_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_promotion_reservation_status CHECK (status IN ('RESERVED','CONSUMED','RELEASED','EXPIRED','REFUNDED')),
    CONSTRAINT uq_promotion_reservation_tx UNIQUE(promotion_id, transaction_ref)
);
CREATE INDEX IF NOT EXISTS idx_promotion_budget_reservation_expiry
    ON promotion_budget_reservation(status, expires_at) WHERE status = 'RESERVED';

CREATE TABLE IF NOT EXISTS promotion_funder_ledger (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    promotion_id BIGINT NOT NULL REFERENCES promotion_budget_account(promotion_id),
    transaction_ref VARCHAR(120) NOT NULL,
    reservation_id UUID REFERENCES promotion_budget_reservation(reservation_id),
    entry_type VARCHAR(24) NOT NULL,
    amount NUMERIC(24,4) NOT NULL CHECK (amount > 0),
    currency VARCHAR(8) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    reversal_of UUID REFERENCES promotion_funder_ledger(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_promotion_ledger_type CHECK (entry_type IN ('DEBIT','CREDIT','REVERSAL'))
);
CREATE INDEX IF NOT EXISTS idx_promotion_funder_ledger_tx
    ON promotion_funder_ledger(transaction_ref, created_at);

CREATE OR REPLACE FUNCTION reserve_promotion_budget(
    p_promotion_id BIGINT,
    p_transaction_ref VARCHAR,
    p_amount NUMERIC,
    p_currency VARCHAR,
    p_expires_at TIMESTAMPTZ
) RETURNS UUID
LANGUAGE plpgsql
AS $$
DECLARE
    existing UUID;
    existing_amount NUMERIC(24,4);
    existing_currency VARCHAR(8);
    new_id UUID;
BEGIN
    IF p_promotion_id <= 0 THEN RAISE EXCEPTION 'Promotion id must be positive'; END IF;
    IF p_amount <= 0 THEN RAISE EXCEPTION 'Promotion reservation amount must be positive'; END IF;
    SELECT reservation_id, amount, currency
      INTO existing, existing_amount, existing_currency
      FROM promotion_budget_reservation
     WHERE promotion_id = p_promotion_id AND transaction_ref = p_transaction_ref;
    IF existing IS NOT NULL THEN
        IF existing_amount <> p_amount OR existing_currency <> p_currency THEN
            RAISE EXCEPTION 'Promotion reservation idempotency conflict';
        END IF;
        RETURN existing;
    END IF;

    PERFORM pg_advisory_xact_lock(p_promotion_id);

    -- Re-check after acquiring the lock so concurrent retries with the same
    -- transaction reference remain idempotent instead of racing the unique key.
    SELECT reservation_id, amount, currency
      INTO existing, existing_amount, existing_currency
      FROM promotion_budget_reservation
     WHERE promotion_id = p_promotion_id AND transaction_ref = p_transaction_ref;
    IF existing IS NOT NULL THEN
        IF existing_amount <> p_amount OR existing_currency <> p_currency THEN
            RAISE EXCEPTION 'Promotion reservation idempotency conflict';
        END IF;
        RETURN existing;
    END IF;

    UPDATE promotion_budget_account
       SET reserved_amount = reserved_amount + p_amount,
           version = version + 1,
           updated_at = now()
     WHERE promotion_id = p_promotion_id
       AND currency = p_currency
       AND reserved_amount + consumed_amount + p_amount <= budget_cap;
    IF NOT FOUND THEN RAISE EXCEPTION 'Promotion budget exhausted or currency mismatch'; END IF;

    INSERT INTO promotion_budget_reservation(
        promotion_id, transaction_ref, amount, currency, expires_at)
    VALUES (p_promotion_id, p_transaction_ref, p_amount, p_currency, p_expires_at)
    RETURNING reservation_id INTO new_id;
    RETURN new_id;
END;
$$;
