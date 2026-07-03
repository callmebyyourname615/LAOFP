ALTER TABLE connector_configs
    ADD COLUMN IF NOT EXISTS mock_dispatch_mode VARCHAR(32),
    ADD COLUMN IF NOT EXISTS mock_status_enquiry_result VARCHAR(32);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_connector_configs_mock_dispatch_mode'
    ) THEN
        ALTER TABLE connector_configs
            ADD CONSTRAINT chk_connector_configs_mock_dispatch_mode
                CHECK (mock_dispatch_mode IS NULL OR mock_dispatch_mode IN ('SUCCESS', 'TIMEOUT', 'REJECT'));
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_connector_configs_mock_status_enquiry_result'
    ) THEN
        ALTER TABLE connector_configs
            ADD CONSTRAINT chk_connector_configs_mock_status_enquiry_result
                CHECK (mock_status_enquiry_result IS NULL OR mock_status_enquiry_result IN (
                    'ACCEPTED', 'REJECTED', 'NOT_FOUND', 'PROCESSING', 'UNKNOWN'
                ));
    END IF;
END $$;
