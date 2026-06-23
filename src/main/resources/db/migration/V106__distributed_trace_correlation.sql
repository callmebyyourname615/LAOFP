-- Phase 62J: persist W3C trace correlation without storing financial payloads.
ALTER TABLE outbox_messages ADD COLUMN IF NOT EXISTS trace_id VARCHAR(32);
ALTER TABLE transaction_events ADD COLUMN IF NOT EXISTS trace_id VARCHAR(32);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS trace_id VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_outbox_messages_trace_id
    ON outbox_messages(trace_id) WHERE trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_transaction_events_trace_id
    ON transaction_events(trace_id, business_date) WHERE trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_audit_logs_trace_id
    ON audit_logs(trace_id) WHERE trace_id IS NOT NULL;
