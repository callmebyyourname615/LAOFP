ALTER TABLE refund_transactions
    ADD COLUMN IF NOT EXISTS last_error TEXT;
