-- ============================================================
-- V25 · Risk Engine — Velocity Checks (P19)
--
--   velocity_checks — sliding-window counters per PSP
--
--   check_type:
--     AMOUNT_DAILY   — total LAK amount in last 24 h
--     COUNT_HOURLY   — transaction count in last 1 h
--     COUNT_DAILY    — transaction count in last 24 h
-- ============================================================

CREATE TABLE velocity_checks (
    check_id        BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    psp_id          VARCHAR(32)   NOT NULL REFERENCES participants(bank_code) ON DELETE CASCADE,
    check_type      VARCHAR(20)   NOT NULL,
    window_start    TIMESTAMP(3)  NOT NULL,
    window_end      TIMESTAMP(3)  NOT NULL,
    current_value   DECIMAL(20,4) NOT NULL DEFAULT 0,
    limit_value     DECIMAL(20,4) NOT NULL,
    breached        BOOLEAN       NOT NULL DEFAULT FALSE,
    last_updated_at TIMESTAMP(3)  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_velocity_type CHECK (check_type IN ('AMOUNT_DAILY', 'COUNT_HOURLY', 'COUNT_DAILY')),
    CONSTRAINT uq_velocity_psp_type_window UNIQUE (psp_id, check_type, window_start)
);

CREATE INDEX idx_velocity_psp_type   ON velocity_checks(psp_id, check_type, window_start DESC);
CREATE INDEX idx_velocity_breached   ON velocity_checks(breached, window_start) WHERE breached = TRUE;
