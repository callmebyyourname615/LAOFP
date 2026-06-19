ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS previous_hash VARCHAR(64);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS entry_hash VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS ux_audit_logs_entry_hash ON audit_logs(entry_hash) WHERE entry_hash IS NOT NULL;
COMMENT ON COLUMN audit_logs.entry_hash IS 'Application-generated SHA-256 hash chaining immutable audit entries';
COMMENT ON COLUMN audit_logs.previous_hash IS 'Hash of the preceding audit entry at insert time';
