SELECT schemaname,tablename,indexname,indexdef FROM pg_indexes
WHERE tablename IN ('transactions','outbox_messages','participants','connector_configs','connector_credentials','settlement_positions')
ORDER BY tablename,indexname;
