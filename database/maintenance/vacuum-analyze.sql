\set ON_ERROR_STOP on
VACUUM (ANALYZE, SKIP_LOCKED) participants;
VACUUM (ANALYZE, SKIP_LOCKED) connector_configs;
VACUUM (ANALYZE, SKIP_LOCKED) routing_rules;
VACUUM (ANALYZE, SKIP_LOCKED) outbox_messages;
VACUUM (ANALYZE, SKIP_LOCKED) outbox_dead_letters;
VACUUM (ANALYZE, SKIP_LOCKED) webhook_delivery_log;
VACUUM (ANALYZE, SKIP_LOCKED) sanctions_entries;
VACUUM (ANALYZE, SKIP_LOCKED) audit_logs;
ANALYZE transactions;
ANALYZE inquiries;
ANALYZE transaction_events;
ANALYZE iso_messages;
ANALYZE settlement_items;
ANALYZE reconciliation_items;
