-- ============================================================
-- V32 · Settlement instruction RTGS payload tracking
--
--   Adds request/response/error persistence for controlled
--   pacs.009 submission and RTGS callback retry diagnostics.
-- ============================================================

ALTER TABLE settlement_instructions
    ADD COLUMN IF NOT EXISTS rtgs_request_payload TEXT NULL,
    ADD COLUMN IF NOT EXISTS rtgs_response_payload TEXT NULL,
    ADD COLUMN IF NOT EXISTS last_error TEXT NULL;
