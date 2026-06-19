-- ============================================================
-- V15 · Legacy safety guard
--
-- In a fresh install this file is a no-op.
-- On a database migrated from the old schema it drops the
-- obsolete tables that no longer exist in the new design:
--   participant_banks  — replaced by participants
--   iso_inquiries      — merged into inquiries
--   transfers          — replaced by transactions
--   outbox_events      — replaced by outbox_messages
--   transfer_status_history — replaced by transaction_status_history
-- ============================================================

DROP TABLE IF EXISTS participant_banks       CASCADE;
DROP TABLE IF EXISTS iso_inquiries           CASCADE;
DROP TABLE IF EXISTS transfers               CASCADE;
DROP TABLE IF EXISTS transfer_status_history CASCADE;
DROP TABLE IF EXISTS outbox_events           CASCADE;
