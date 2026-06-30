ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS confirmation_status VARCHAR(32) NOT NULL DEFAULT 'CONFIRMED',
    ADD COLUMN IF NOT EXISTS settlement_confidence VARCHAR(32) NOT NULL DEFAULT 'CONFIRMED';

UPDATE transactions
SET confirmation_status = CASE
        WHEN status = 'ACCEPTED' THEN 'PENDING'
        WHEN status = 'DRS_REQUIRED' THEN 'DISPUTED'
        ELSE confirmation_status
    END,
    settlement_confidence = CASE
        WHEN status = 'ACCEPTED' THEN 'PENDING'
        WHEN status = 'DRS_REQUIRED' THEN 'DISPUTED'
        ELSE settlement_confidence
    END;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_transactions_confirmation_status'
    ) THEN
        ALTER TABLE transactions
            ADD CONSTRAINT ck_transactions_confirmation_status
            CHECK (confirmation_status IN ('PENDING', 'CONFIRMED', 'PROVISIONAL', 'DISPUTED'));
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_transactions_settlement_confidence'
    ) THEN
        ALTER TABLE transactions
            ADD CONSTRAINT ck_transactions_settlement_confidence
            CHECK (settlement_confidence IN ('PENDING', 'CONFIRMED', 'PROVISIONAL', 'DISPUTED'));
    END IF;
END $$;
