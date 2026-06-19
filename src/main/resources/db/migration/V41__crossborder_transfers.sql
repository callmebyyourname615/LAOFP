-- V41: Cross-border Payment — transfer records (P17)
CREATE TABLE crossborder_transfers (
    cb_id                BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    quote_id             BIGINT        NOT NULL REFERENCES fx_quotes(quote_id),
    txn_ref              VARCHAR(200),             -- SETTLED transaction_ref
    initiating_psp_id    VARCHAR(32)   NOT NULL,
    purpose_code         VARCHAR(50),
    source_of_funds      VARCHAR(200),
    beneficiary_name     VARCHAR(200)  NOT NULL,
    beneficiary_bank     VARCHAR(200)  NOT NULL,
    beneficiary_account  VARCHAR(200)  NOT NULL,
    beneficiary_country  VARCHAR(3)    NOT NULL,
    target_network       VARCHAR(10)   NOT NULL,
    network_txn_id       VARCHAR(200),
    compliance_check_id  BIGINT,
    status               VARCHAR(10)   NOT NULL DEFAULT 'INITIATED',
    initiated_at         TIMESTAMP(3)  NOT NULL DEFAULT NOW(),
    completed_at         TIMESTAMP(3),

    CONSTRAINT chk_cb_status CHECK (status IN ('INITIATED','COMPLETED','FAILED'))
);

CREATE INDEX idx_cb_quote ON crossborder_transfers(quote_id);
CREATE INDEX idx_cb_psp   ON crossborder_transfers(initiating_psp_id, status);
