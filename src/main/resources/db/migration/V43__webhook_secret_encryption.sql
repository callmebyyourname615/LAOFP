-- Expand phase for envelope-encrypted webhook signing secrets.
-- The migration Job applies V43, encrypts all legacy secret_plain values through
-- Vault/KMS, then continues to V44 in the same one-shot process.
ALTER TABLE webhook_registrations
    ADD COLUMN secret_ciphertext TEXT,
    ADD COLUMN secret_key_id VARCHAR(200),
    ADD COLUMN secret_version INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN previous_secret_ciphertext TEXT,
    ADD COLUMN previous_secret_expires_at TIMESTAMP(3);

ALTER TABLE webhook_registrations
    ADD CONSTRAINT chk_webhook_secret_version_positive CHECK (secret_version > 0);
