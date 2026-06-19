-- Contract phase. Refuse to remove the legacy column until every row has been
-- encrypted by WebhookSecretBackfillService in the migration Job.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM webhook_registrations
        WHERE secret_ciphertext IS NULL
           OR secret_key_id IS NULL
    ) THEN
        RAISE EXCEPTION 'Webhook secret encryption backfill is incomplete; refusing to drop secret_plain';
    END IF;
END $$;

ALTER TABLE webhook_registrations
    ALTER COLUMN secret_ciphertext SET NOT NULL,
    ALTER COLUMN secret_key_id SET NOT NULL;

ALTER TABLE webhook_registrations
    DROP COLUMN secret_plain;
