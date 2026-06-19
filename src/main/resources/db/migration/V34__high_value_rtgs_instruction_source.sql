-- ============================================================
-- V34 · High-value RTGS instruction source tracking
--
-- Allows settlement_instructions to represent either DNS cycle
-- net positions or a single high-value transfer routed to RTGS.
-- ============================================================

ALTER TABLE settlement_instructions
    ALTER COLUMN cycle_id DROP NOT NULL;

ALTER TABLE settlement_instructions
    ADD COLUMN source_type VARCHAR(24) NOT NULL DEFAULT 'DNS_CYCLE',
    ADD COLUMN transfer_ref VARCHAR(64) NULL;

ALTER TABLE settlement_instructions
    ADD CONSTRAINT ck_settlement_instructions_source
        CHECK (
            (source_type = 'DNS_CYCLE' AND cycle_id IS NOT NULL AND transfer_ref IS NULL)
            OR
            (source_type = 'HIGH_VALUE_TRANSFER' AND cycle_id IS NULL AND transfer_ref IS NOT NULL)
        );

CREATE UNIQUE INDEX uq_settlement_instructions_transfer_ref
    ON settlement_instructions(transfer_ref)
    WHERE transfer_ref IS NOT NULL;

CREATE INDEX idx_settlement_instructions_source_status
    ON settlement_instructions(source_type, status);
