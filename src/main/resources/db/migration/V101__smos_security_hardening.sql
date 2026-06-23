-- Phase 61D: production hardening for SMOS identity, participant scope and session replay controls.
ALTER TABLE smos_users
    ADD COLUMN participant_id BIGINT REFERENCES participants(id) ON DELETE RESTRICT,
    ADD COLUMN password_changed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN mfa_enrolled_at TIMESTAMPTZ,
    ADD COLUMN locked_at TIMESTAMPTZ,
    ADD COLUMN last_failed_login_at TIMESTAMPTZ;

UPDATE smos_users SET mfa_enrolled_at = created_at
WHERE mfa_secret_ciphertext IS NOT NULL AND mfa_enrolled_at IS NULL;

ALTER TABLE smos_users
    ADD CONSTRAINT ck_smos_participant_admin_scope
    CHECK (participant_id IS NULL OR participant_id > 0);

CREATE INDEX ix_smos_users_participant_id ON smos_users(participant_id)
    WHERE participant_id IS NOT NULL;

ALTER TABLE smos_auth_sessions
    ADD COLUMN session_family_id UUID,
    ADD COLUMN rotated_from_id UUID REFERENCES smos_auth_sessions(id) ON DELETE SET NULL,
    ADD COLUMN last_used_at TIMESTAMPTZ,
    ADD COLUMN client_fingerprint_hash VARCHAR(64);

UPDATE smos_auth_sessions SET session_family_id = id WHERE session_family_id IS NULL;
ALTER TABLE smos_auth_sessions ALTER COLUMN session_family_id SET NOT NULL;
ALTER TABLE smos_auth_sessions
    ADD CONSTRAINT ck_smos_client_fingerprint_hash
    CHECK (client_fingerprint_hash IS NULL OR client_fingerprint_hash ~ '^[0-9a-f]{64}$');
CREATE INDEX ix_smos_auth_sessions_family ON smos_auth_sessions(session_family_id, created_at);
CREATE INDEX ix_smos_auth_sessions_user_active ON smos_auth_sessions(user_id, expires_at)
    WHERE revoked_at IS NULL;

INSERT INTO smos_permissions(resource, action, description) VALUES
 ('session','view','View own active SMOS sessions'),
 ('session','revoke','Revoke own active SMOS sessions'),
 ('security','reset_mfa','Reset MFA through maker-checker'),
 ('security','reset_password','Reset an operator password through maker-checker')
ON CONFLICT (resource, action) DO NOTHING;

INSERT INTO smos_role_permissions(role_id, permission_id)
SELECT r.id, p.id FROM smos_roles r CROSS JOIN smos_permissions p
WHERE r.name = 'SYSTEM_ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO smos_role_permissions(role_id, permission_id)
SELECT r.id, p.id FROM smos_roles r JOIN smos_permissions p
  ON p.resource = 'session' AND p.action IN ('view','revoke')
WHERE r.name IN ('OPS_ADMIN','SETTLEMENT_OFFICER','DISPUTE_OFFICER','RISK_OFFICER','AUDITOR','PARTICIPANT_ADMIN','READ_ONLY')
ON CONFLICT DO NOTHING;
