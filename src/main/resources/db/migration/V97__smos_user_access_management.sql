-- SMOS User & Access Management: operators, RBAC, MFA sessions and maker-checker.
CREATE TABLE smos_roles (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(40) NOT NULL UNIQUE,
    description VARCHAR(256) NOT NULL
);

CREATE TABLE smos_permissions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    resource VARCHAR(64) NOT NULL,
    action VARCHAR(32) NOT NULL,
    description VARCHAR(256) NOT NULL,
    CONSTRAINT uq_smos_permission UNIQUE (resource, action)
);

CREATE TABLE smos_users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(128) NOT NULL,
    email VARCHAR(160) NOT NULL,
    full_name VARCHAR(160) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    mfa_secret_ciphertext TEXT,
    failed_login_count INTEGER NOT NULL DEFAULT 0,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_smos_user_status CHECK (status IN ('ACTIVE','LOCKED','DISABLED')),
    CONSTRAINT ck_smos_failed_login_count CHECK (failed_login_count >= 0)
);
CREATE UNIQUE INDEX uq_smos_users_username_ci ON smos_users (lower(username));
CREATE UNIQUE INDEX uq_smos_users_email_ci ON smos_users (lower(email));
CREATE TRIGGER trg_smos_users_updated_at BEFORE UPDATE ON smos_users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE smos_user_roles (
    user_id BIGINT NOT NULL REFERENCES smos_users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES smos_roles(id) ON DELETE RESTRICT,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    granted_by BIGINT REFERENCES smos_users(id),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE smos_role_permissions (
    role_id BIGINT NOT NULL REFERENCES smos_roles(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES smos_permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE smos_auth_sessions (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES smos_users(id) ON DELETE CASCADE,
    session_type VARCHAR(24) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_smos_session_type CHECK (session_type IN ('MFA_CHALLENGE','REFRESH_TOKEN')),
    CONSTRAINT ck_smos_session_token_hash CHECK (token_hash ~ '^[0-9a-f]{64}$')
);
CREATE INDEX ix_smos_auth_sessions_active
    ON smos_auth_sessions(user_id, session_type, expires_at)
    WHERE revoked_at IS NULL;

CREATE TABLE smos_maker_checker_requests (
    id UUID PRIMARY KEY,
    request_type VARCHAR(64) NOT NULL,
    payload_json JSONB NOT NULL,
    payload_sha256 VARCHAR(64) NOT NULL,
    maker_id BIGINT NOT NULL REFERENCES smos_users(id),
    checker_id BIGINT REFERENCES smos_users(id),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_at TIMESTAMPTZ,
    decision_notes VARCHAR(512),
    execution_reference VARCHAR(160),
    CONSTRAINT ck_smos_mc_status CHECK (status IN ('PENDING','APPROVED','REJECTED','EXPIRED','CANCELLED')),
    CONSTRAINT ck_smos_mc_different_actor CHECK (checker_id IS NULL OR checker_id <> maker_id),
    CONSTRAINT ck_smos_mc_payload_sha CHECK (payload_sha256 ~ '^[0-9a-f]{64}$')
);
CREATE INDEX ix_smos_mc_pending
    ON smos_maker_checker_requests(status, submitted_at)
    WHERE status = 'PENDING';

INSERT INTO smos_roles(name, description) VALUES
 ('SYSTEM_ADMIN','Full SMOS administration and all operational permissions'),
 ('OPS_ADMIN','Operations administration excluding user-security ownership'),
 ('SETTLEMENT_OFFICER','Settlement queue, approval and settlement dashboards'),
 ('DISPUTE_OFFICER','Dispute investigation and resolution'),
 ('RISK_OFFICER','Risk, fraud and sanctions investigation'),
 ('AUDITOR','Read-only audit and operational evidence access'),
 ('PARTICIPANT_ADMIN','Manage configuration scoped to own participant'),
 ('READ_ONLY','Read-only dashboards and operational status');

INSERT INTO smos_permissions(resource, action, description) VALUES
 ('user','view','View SMOS users and role assignments'),
 ('user','manage','Create, disable, unlock and assign operator roles'),
 ('settlement','view','View settlement cycles, positions and instructions'),
 ('settlement','approve','Approve settlement instructions through maker-checker'),
 ('dispute','view','View dispute cases'),
 ('dispute','resolve','Resolve dispute cases through approved workflow'),
 ('participant','view','View participant configuration'),
 ('participant','manage','Manage participant configuration'),
 ('risk','view','View risk, fraud and sanctions alerts'),
 ('risk','investigate','Investigate and disposition risk alerts'),
 ('audit','read','Read immutable audit trail and evidence'),
 ('dashboard','settlement','View settlement dashboard'),
 ('dashboard','risk','View risk dashboard'),
 ('dashboard','crossborder','View cross-border dashboard'),
 ('maker_checker','submit','Submit controlled actions for approval'),
 ('maker_checker','approve','Approve or reject controlled actions created by another user');

-- SYSTEM_ADMIN receives every permission.
INSERT INTO smos_role_permissions(role_id, permission_id)
SELECT r.id, p.id FROM smos_roles r CROSS JOIN smos_permissions p
WHERE r.name = 'SYSTEM_ADMIN';

-- OPS_ADMIN receives operational permissions, but not user.manage.
INSERT INTO smos_role_permissions(role_id, permission_id)
SELECT r.id, p.id FROM smos_roles r CROSS JOIN smos_permissions p
WHERE r.name = 'OPS_ADMIN' AND NOT (p.resource = 'user' AND p.action = 'manage');

INSERT INTO smos_role_permissions(role_id, permission_id)
SELECT r.id, p.id FROM smos_roles r JOIN smos_permissions p ON
 (p.resource = 'settlement' AND p.action IN ('view','approve')) OR
 (p.resource = 'dashboard' AND p.action = 'settlement') OR
 (p.resource = 'maker_checker' AND p.action IN ('submit','approve'))
WHERE r.name = 'SETTLEMENT_OFFICER';

INSERT INTO smos_role_permissions(role_id, permission_id)
SELECT r.id, p.id FROM smos_roles r JOIN smos_permissions p ON
 (p.resource = 'dispute' AND p.action IN ('view','resolve')) OR
 (p.resource = 'maker_checker' AND p.action IN ('submit','approve'))
WHERE r.name = 'DISPUTE_OFFICER';

INSERT INTO smos_role_permissions(role_id, permission_id)
SELECT r.id, p.id FROM smos_roles r JOIN smos_permissions p ON
 (p.resource = 'risk' AND p.action IN ('view','investigate')) OR
 (p.resource = 'dashboard' AND p.action = 'risk') OR
 (p.resource = 'maker_checker' AND p.action IN ('submit','approve'))
WHERE r.name = 'RISK_OFFICER';

INSERT INTO smos_role_permissions(role_id, permission_id)
SELECT r.id, p.id FROM smos_roles r JOIN smos_permissions p ON
 (p.resource = 'audit' AND p.action = 'read') OR
 (p.resource = 'user' AND p.action = 'view') OR
 (p.resource = 'settlement' AND p.action = 'view') OR
 (p.resource = 'dispute' AND p.action = 'view') OR
 (p.resource = 'participant' AND p.action = 'view') OR
 (p.resource = 'risk' AND p.action = 'view') OR
 (p.resource = 'dashboard')
WHERE r.name = 'AUDITOR';

INSERT INTO smos_role_permissions(role_id, permission_id)
SELECT r.id, p.id FROM smos_roles r JOIN smos_permissions p ON
 (p.resource = 'participant' AND p.action IN ('view','manage')) OR
 (p.resource = 'maker_checker' AND p.action = 'submit')
WHERE r.name = 'PARTICIPANT_ADMIN';

INSERT INTO smos_role_permissions(role_id, permission_id)
SELECT r.id, p.id FROM smos_roles r JOIN smos_permissions p ON
 (p.resource = 'settlement' AND p.action = 'view') OR
 (p.resource = 'dispute' AND p.action = 'view') OR
 (p.resource = 'participant' AND p.action = 'view') OR
 (p.resource = 'risk' AND p.action = 'view') OR
 (p.resource = 'dashboard')
WHERE r.name = 'READ_ONLY';
