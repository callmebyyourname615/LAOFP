-- ============================================================
-- V28 · Fix settlement_positions FK to support participant teardown
--
--   Re-add settlement_positions.bank_code FK with ON DELETE CASCADE
--   so that deleting a participant row also removes its net positions.
--   (Required for test isolation in OperationsGenerateRoutesForBankIntegrationTest.)
-- ============================================================

ALTER TABLE settlement_positions
    DROP CONSTRAINT IF EXISTS settlement_positions_bank_code_fkey;

ALTER TABLE settlement_positions
    ADD CONSTRAINT settlement_positions_bank_code_fkey
        FOREIGN KEY (bank_code)
        REFERENCES participants(bank_code)
        ON DELETE CASCADE;
