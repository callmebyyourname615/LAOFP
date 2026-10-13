package com.example.switching;

import static com.example.switching.TestDemoBanks.ADMIN_KEY;
import static com.example.switching.TestDemoBanks.BANK_A;
import static com.example.switching.TestDemoBanks.BANK_A_KEY;
import static com.example.switching.TestDemoBanks.BANK_B;
import static com.example.switching.TestDemoBanks.BANK_B_KEY;
import static com.example.switching.TestDemoBanks.BANK_C;
import static com.example.switching.TestDemoBanks.CLIENT_BANK_A;
import static com.example.switching.TestDemoBanks.CLIENT_BANK_A_SECRET;
import static com.example.switching.TestDemoBanks.CLIENT_BANK_B;
import static com.example.switching.TestDemoBanks.CLIENT_BANK_B_SECRET;
import static com.example.switching.TestDemoBanks.MOCK_BANK_A_CONNECTOR;
import static com.example.switching.TestDemoBanks.MOCK_BANK_B_CONNECTOR;
import static com.example.switching.TestDemoBanks.MOCK_BANK_C_CONNECTOR;
import static com.example.switching.TestDemoBanks.OPS_KEY;
import static com.example.switching.TestDemoBanks.ROUTE_BANK_A_TO_BANK_B;
import static com.example.switching.TestDemoBanks.ROUTE_BANK_A_TO_BANK_C;
import static com.example.switching.TestDemoBanks.ROUTE_BANK_B_TO_BANK_A;
import static com.example.switching.TestDemoBanks.ROUTE_BANK_C_TO_BANK_A;

import org.springframework.jdbc.core.JdbcTemplate;

final class TestDemoBankSeed {

    private TestDemoBankSeed() {
    }

    static void seed(JdbcTemplate jdbcTemplate) {
        seedParticipants(jdbcTemplate);
        seedPools(jdbcTemplate);
        seedConnectors(jdbcTemplate);
        seedRoutes(jdbcTemplate);
        seedBootstrapApiKeys(jdbcTemplate);
        seedApiKeys(jdbcTemplate);
        seedOauthClients(jdbcTemplate);
        seedCertificates(jdbcTemplate);
    }

    private static void seedParticipants(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update(
                """
                INSERT INTO participants (bank_code, bank_name, status, participant_type, country, currency, created_at)
                VALUES
                    (?, 'Bank A', 'ACTIVE', 'DIRECT',   'LA', 'LAK', NOW()),
                    (?, 'Bank B', 'ACTIVE', 'DIRECT',   'LA', 'LAK', NOW()),
                    (?, 'Bank C', 'ACTIVE', 'INDIRECT', 'LA', 'LAK', NOW())
                ON CONFLICT (bank_code) DO UPDATE SET
                    bank_name = EXCLUDED.bank_name,
                    status = EXCLUDED.status,
                    participant_type = EXCLUDED.participant_type,
                    country = EXCLUDED.country,
                    currency = EXCLUDED.currency,
                    updated_at = NOW()
                """,
                BANK_A, BANK_B, BANK_C);
    }

    private static void seedPools(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update(
                """
                INSERT INTO psp_pools (psp_id, balance, held_amount, currency, minimum_balance)
                VALUES
                    (?, 0, 0, 'LAK', 100000000),
                    (?, 0, 0, 'LAK', 100000000),
                    (?, 0, 0, 'LAK', 100000000)
                ON CONFLICT (psp_id) DO NOTHING
                """,
                BANK_A, BANK_B, BANK_C);
    }

    private static void seedConnectors(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update(
                """
                INSERT INTO connector_configs (
                    connector_name, bank_code, connector_type, endpoint_url, timeout_ms,
                    enabled, force_reject, reject_reason_code, reject_reason_message, created_at
                ) VALUES
                    (?, ?, 'MOCK', NULL, 5000, TRUE, FALSE, 'AC01', 'Mock Bank A rejected', NOW()),
                    (?, ?, 'MOCK', NULL, 5000, TRUE, FALSE, 'AC01', 'Mock Bank B rejected', NOW()),
                    (?, ?, 'MOCK', NULL, 5000, TRUE, FALSE, 'AC01', 'Mock Bank C rejected', NOW())
                ON CONFLICT (connector_name) DO UPDATE SET
                    bank_code = EXCLUDED.bank_code,
                    connector_type = EXCLUDED.connector_type,
                    endpoint_url = EXCLUDED.endpoint_url,
                    timeout_ms = EXCLUDED.timeout_ms,
                    enabled = EXCLUDED.enabled,
                    force_reject = EXCLUDED.force_reject,
                    reject_reason_code = EXCLUDED.reject_reason_code,
                    reject_reason_message = EXCLUDED.reject_reason_message,
                    updated_at = NOW()
                """,
                MOCK_BANK_A_CONNECTOR, BANK_A,
                MOCK_BANK_B_CONNECTOR, BANK_B,
                MOCK_BANK_C_CONNECTOR, BANK_C);
    }

    private static void seedRoutes(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update(
                """
                INSERT INTO routing_rules (
                    route_code, source_bank, destination_bank, message_type, connector_name, priority, enabled, created_at
                ) VALUES
                    (?, ?, ?, 'PACS_008', ?, 1, TRUE, NOW()),
                    (?, ?, ?, 'PACS_008', ?, 1, TRUE, NOW()),
                    (?, ?, ?, 'PACS_008', ?, 1, TRUE, NOW()),
                    (?, ?, ?, 'PACS_008', ?, 1, TRUE, NOW())
                ON CONFLICT (route_code) DO UPDATE SET
                    source_bank = EXCLUDED.source_bank,
                    destination_bank = EXCLUDED.destination_bank,
                    message_type = EXCLUDED.message_type,
                    connector_name = EXCLUDED.connector_name,
                    priority = EXCLUDED.priority,
                    enabled = EXCLUDED.enabled,
                    updated_at = NOW()
                """,
                ROUTE_BANK_A_TO_BANK_B, BANK_A, BANK_B, MOCK_BANK_B_CONNECTOR,
                ROUTE_BANK_A_TO_BANK_C, BANK_A, BANK_C, MOCK_BANK_C_CONNECTOR,
                ROUTE_BANK_B_TO_BANK_A, BANK_B, BANK_A, MOCK_BANK_A_CONNECTOR,
                ROUTE_BANK_C_TO_BANK_A, BANK_C, BANK_A, MOCK_BANK_A_CONNECTOR);
    }

    private static void seedBootstrapApiKeys(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update(
                """
                INSERT INTO api_keys (key_value, key_prefix, name, role, bank_code, enabled, created_at)
                VALUES
                    (encode(digest(?::bytea, 'sha256'), 'hex'), 'sk-admin-switc', 'Admin Key', 'ADMIN', NULL, TRUE, NOW()),
                    (encode(digest(?::bytea, 'sha256'), 'hex'), 'sk-ops-switchi', 'Operations Key', 'OPS', NULL, TRUE, NOW())
                ON CONFLICT (key_value) DO UPDATE SET
                    key_prefix = EXCLUDED.key_prefix,
                    name = EXCLUDED.name,
                    role = EXCLUDED.role,
                    bank_code = EXCLUDED.bank_code,
                    enabled = EXCLUDED.enabled
                """,
                ADMIN_KEY, OPS_KEY);
    }

    private static void seedApiKeys(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update(
                """
                INSERT INTO api_keys (key_value, key_prefix, name, role, bank_code, enabled, created_at)
                VALUES
                    (encode(digest(?::bytea, 'sha256'), 'hex'), 'sk-bank-a-swit', 'Bank A Key', 'BANK', ?, TRUE, NOW()),
                    (encode(digest(?::bytea, 'sha256'), 'hex'), 'sk-bank-b-swit', 'Bank B Key', 'BANK', ?, TRUE, NOW())
                ON CONFLICT (key_value) DO UPDATE SET
                    key_prefix = EXCLUDED.key_prefix,
                    name = EXCLUDED.name,
                    role = EXCLUDED.role,
                    bank_code = EXCLUDED.bank_code,
                    enabled = EXCLUDED.enabled
                """,
                BANK_A_KEY, BANK_A, BANK_B_KEY, BANK_B);
    }

    private static void seedOauthClients(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update(
                """
                INSERT INTO oauth_clients (client_id, psp_id, client_secret_hash, tier, scopes, status, created_at)
                VALUES
                    (?, ?,
                     encode(digest(?::bytea, 'sha256'), 'hex'),
                     'TIER1', 'payments:write inquiries:write payments:read', 'ACTIVE', NOW()),
                    (?, ?,
                     encode(digest(?::bytea, 'sha256'), 'hex'),
                     'TIER1', 'payments:write inquiries:write payments:read', 'ACTIVE', NOW())
                ON CONFLICT (client_id) DO UPDATE SET
                    psp_id = EXCLUDED.psp_id,
                    client_secret_hash = EXCLUDED.client_secret_hash,
                    tier = EXCLUDED.tier,
                    scopes = EXCLUDED.scopes,
                    status = EXCLUDED.status
                """,
                CLIENT_BANK_A, BANK_A, CLIENT_BANK_A_SECRET,
                CLIENT_BANK_B, BANK_B, CLIENT_BANK_B_SECRET);
    }

    private static void seedCertificates(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update(
                """
                INSERT INTO psp_certificates (
                    cert_id, psp_id, cert_fingerprint, subject_dn, issued_at, expires_at, status, created_at
                ) VALUES
                    ('seed-bank-a-mtls-cert', ?,
                     '41f82ec6da7fd8e7e29fba6916637e5a89e0e21e698eab3b9a845ec5184f19ca',
                     'CN=BANK_A_MTLS_TEST,O=LaoFP,C=LA',
                     '2026-05-19 04:33:36', '2036-05-16 04:33:36', 'ACTIVE', NOW()),
                    ('seed-bank-b-mtls-cert', ?,
                     '8e441136c3b88432eab101a3e30b001fdf6c6d7e46f9641b28e6cb4a24bb9d8a',
                     'CN=BANK_B_MTLS_SEED,O=LaoFP,C=LA',
                     '2026-05-19 06:14:50', '2036-05-16 06:14:50', 'ACTIVE', NOW())
                ON CONFLICT (cert_id) DO UPDATE SET
                    psp_id = EXCLUDED.psp_id,
                    cert_fingerprint = EXCLUDED.cert_fingerprint,
                    subject_dn = EXCLUDED.subject_dn,
                    issued_at = EXCLUDED.issued_at,
                    expires_at = EXCLUDED.expires_at,
                    status = EXCLUDED.status
                """,
                BANK_A, BANK_B);
    }
}
