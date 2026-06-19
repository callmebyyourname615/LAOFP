-- ============================================================
-- V13 · Seed data
--     Participants, routing rules, connector configs,
--     API keys (hashed), OAuth clients, PSP certificates
-- ============================================================

-- ── Participants ─────────────────────────────────────────────
INSERT INTO participants (bank_code, bank_name, status, participant_type, country, currency, created_at)
VALUES
    ('BANK_A', 'Bank A', 'ACTIVE', 'DIRECT',   'LA', 'LAK', NOW()),
    ('BANK_B', 'Bank B', 'ACTIVE', 'DIRECT',   'LA', 'LAK', NOW()),
    ('BANK_C', 'Bank C', 'ACTIVE', 'INDIRECT', 'LA', 'LAK', NOW())
ON CONFLICT (bank_code) DO UPDATE
    SET bank_name = EXCLUDED.bank_name, updated_at = NOW();

-- ── Connector configs ─────────────────────────────────────────
INSERT INTO connector_configs (
    connector_name, bank_code, connector_type, endpoint_url, timeout_ms,
    enabled, force_reject, reject_reason_code, reject_reason_message, created_at
) VALUES
    ('MOCK_BANK_A_CONNECTOR', 'BANK_A', 'MOCK', NULL, 5000, TRUE, FALSE, 'AC01', 'Mock Bank A rejected', NOW()),
    ('MOCK_BANK_B_CONNECTOR', 'BANK_B', 'MOCK', NULL, 5000, TRUE, FALSE, 'AC01', 'Mock Bank B rejected', NOW()),
    ('MOCK_BANK_C_CONNECTOR', 'BANK_C', 'MOCK', NULL, 5000, TRUE, FALSE, 'AC01', 'Mock Bank C rejected', NOW())
ON CONFLICT (connector_name) DO UPDATE
    SET updated_at = NOW();

-- ── Routing rules ─────────────────────────────────────────────
INSERT INTO routing_rules (route_code, source_bank, destination_bank, message_type, connector_name, priority, enabled, created_at)
VALUES
    ('ROUTE_BANK_A_TO_BANK_B_PACS008', 'BANK_A', 'BANK_B', 'PACS_008', 'MOCK_BANK_B_CONNECTOR', 1, TRUE, NOW()),
    ('ROUTE_BANK_A_TO_BANK_C_PACS008', 'BANK_A', 'BANK_C', 'PACS_008', 'MOCK_BANK_C_CONNECTOR', 1, TRUE, NOW()),
    ('ROUTE_BANK_B_TO_BANK_A_PACS008', 'BANK_B', 'BANK_A', 'PACS_008', 'MOCK_BANK_A_CONNECTOR', 1, TRUE, NOW()),
    ('ROUTE_BANK_C_TO_BANK_A_PACS008', 'BANK_C', 'BANK_A', 'PACS_008', 'MOCK_BANK_A_CONNECTOR', 1, TRUE, NOW())
ON CONFLICT (route_code) DO UPDATE
    SET updated_at = NOW();

-- ── API keys (stored as SHA-256 hex digests) ─────────────────
INSERT INTO api_keys (key_value, key_prefix, name, role, bank_code, enabled, created_at)
VALUES
    (encode(digest('sk-admin-switching-2026'::bytea,  'sha256'), 'hex'), 'sk-admin-switc', 'Admin Key',      'ADMIN', NULL,     TRUE, NOW()),
    (encode(digest('sk-ops-switching-2026'::bytea,    'sha256'), 'hex'), 'sk-ops-switchi', 'Operations Key', 'OPS',   NULL,     TRUE, NOW()),
    (encode(digest('sk-bank-a-switching-2026'::bytea, 'sha256'), 'hex'), 'sk-bank-a-swit', 'Bank A Key',     'BANK',  'BANK_A', TRUE, NOW()),
    (encode(digest('sk-bank-b-switching-2026'::bytea, 'sha256'), 'hex'), 'sk-bank-b-swit', 'Bank B Key',     'BANK',  'BANK_B', TRUE, NOW())
ON CONFLICT (key_value) DO NOTHING;

-- ── OAuth clients ─────────────────────────────────────────────
INSERT INTO oauth_clients (client_id, psp_id, client_secret_hash, tier, scopes, status, created_at)
VALUES
    ('client-bank-a', 'BANK_A',
     encode(digest('secret-bank-a-switching-2026'::bytea, 'sha256'), 'hex'),
     'TIER1', 'payments:write inquiries:write payments:read', 'ACTIVE', NOW()),
    ('client-bank-b', 'BANK_B',
     encode(digest('secret-bank-b-switching-2026'::bytea, 'sha256'), 'hex'),
     'TIER1', 'payments:write inquiries:write payments:read', 'ACTIVE', NOW())
ON CONFLICT (client_id) DO NOTHING;

-- ── PSP mTLS certificates ─────────────────────────────────────
INSERT INTO psp_certificates (cert_id, psp_id, cert_fingerprint, subject_dn, issued_at, expires_at, status, created_at)
VALUES
    ('seed-bank-a-mtls-cert', 'BANK_A',
     '41f82ec6da7fd8e7e29fba6916637e5a89e0e21e698eab3b9a845ec5184f19ca',
     'CN=BANK_A_MTLS_TEST,O=LaoFP,C=LA',
     '2026-05-19 04:33:36', '2036-05-16 04:33:36', 'ACTIVE', NOW()),
    ('seed-bank-b-mtls-cert', 'BANK_B',
     '8e441136c3b88432eab101a3e30b001fdf6c6d7e46f9641b28e6cb4a24bb9d8a',
     'CN=BANK_B_MTLS_SEED,O=LaoFP,C=LA',
     '2026-05-19 06:14:50', '2036-05-16 06:14:50', 'ACTIVE', NOW())
ON CONFLICT (cert_id) DO NOTHING;
