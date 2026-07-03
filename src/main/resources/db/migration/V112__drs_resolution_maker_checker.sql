ALTER TABLE disputes
    ADD COLUMN IF NOT EXISTS proposed_resolution VARCHAR(40),
    ADD COLUMN IF NOT EXISTS proposed_note TEXT,
    ADD COLUMN IF NOT EXISTS proposed_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS proposed_at TIMESTAMP(3),
    ADD COLUMN IF NOT EXISTS checked_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS checked_at TIMESTAMP(3),
    ADD COLUMN IF NOT EXISTS checker_note TEXT;

ALTER TABLE disputes
    DROP CONSTRAINT IF EXISTS chk_dispute_status;

ALTER TABLE disputes
    ADD CONSTRAINT chk_dispute_status CHECK (status IN (
        'OPEN',
        'UNDER_REVIEW',
        'PENDING_APPROVAL',
        'RESOLVED_REFUND',
        'RESOLVED_NO_ACTION',
        'ESCALATED',
        'CLOSED'
    ));

ALTER TABLE disputes
    DROP CONSTRAINT IF EXISTS chk_dispute_proposed_resolution;

ALTER TABLE disputes
    ADD CONSTRAINT chk_dispute_proposed_resolution CHECK (
        proposed_resolution IS NULL OR proposed_resolution IN (
            'NO_ACTION',
            'REFUND_REQUIRED',
            'MANUAL_ADJUSTMENT_REQUIRED'
        )
    );
