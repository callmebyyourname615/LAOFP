ALTER TABLE disputes
    ALTER COLUMN dispute_type TYPE VARCHAR(50);

ALTER TABLE disputes
    DROP CONSTRAINT IF EXISTS chk_dispute_type;

ALTER TABLE disputes
    ADD CONSTRAINT chk_dispute_type CHECK (dispute_type IN (
        'NOT_RECEIVED',
        'WRONG_AMOUNT',
        'DUPLICATE_CHARGE',
        'FRAUD',
        'MERCHANT_DISPUTE',
        'TECHNICAL_ERROR',
        'POST_SETTLEMENT_DESTINATION_DISPUTE'
    ));
