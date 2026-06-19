-- ============================================================
-- V31 · Settlement instructions approval workflow
--
--   settlement_instructions — maker/checker draft instructions
--   generated from settlement net positions before RTGS submission.
-- ============================================================

CREATE TABLE settlement_instructions (
    id                 BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    instruction_ref    VARCHAR(80)   NOT NULL,
    cycle_id           BIGINT        NOT NULL REFERENCES settlement_cycles(id) ON DELETE CASCADE,
    debtor_psp_id      VARCHAR(32)   NOT NULL REFERENCES participants(bank_code),
    creditor_psp_id    VARCHAR(32)   NOT NULL REFERENCES participants(bank_code),
    currency           VARCHAR(8)    NOT NULL DEFAULT 'LAK',
    net_amount         NUMERIC(18,2) NOT NULL,
    status             VARCHAR(24)   NOT NULL DEFAULT 'PENDING_APPROVAL',
    -- PENDING_APPROVAL | APPROVED | REJECTED | SENT_RTGS | CONFIRMED | FAILED
    approval_note      TEXT          NULL,
    approved_by        VARCHAR(100)  NULL,
    approved_at        TIMESTAMP(3)  NULL,
    rejected_by        VARCHAR(100)  NULL,
    rejected_at        TIMESTAMP(3)  NULL,
    rejection_reason   TEXT          NULL,
    rtgs_msg_id        VARCHAR(100)  NULL,
    sent_at            TIMESTAMP(3)  NULL,
    confirmed_at       TIMESTAMP(3)  NULL,
    created_at         TIMESTAMP(3)  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP(3)  NULL,
    CONSTRAINT uq_settlement_instructions_ref UNIQUE (instruction_ref),
    CONSTRAINT ck_settlement_instructions_amount CHECK (net_amount > 0),
    CONSTRAINT ck_settlement_instructions_parties CHECK (debtor_psp_id <> creditor_psp_id)
);

CREATE INDEX idx_settlement_instructions_cycle
    ON settlement_instructions(cycle_id, status);

CREATE INDEX idx_settlement_instructions_debtor
    ON settlement_instructions(debtor_psp_id, status);

CREATE INDEX idx_settlement_instructions_creditor
    ON settlement_instructions(creditor_psp_id, status);

CREATE TRIGGER trg_settlement_instructions_updated_at
    BEFORE UPDATE ON settlement_instructions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
