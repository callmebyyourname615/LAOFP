-- ============================================================
-- V117 · Remove legacy demo seed data
--
-- V13 no longer seeds demo participants, mock routes, PSP
-- credentials, or bootstrap API keys on clean installs. This
-- forward migration removes those legacy rows from databases that
-- applied the older V13 contents before that extraction.
-- ============================================================

WITH legacy_participants AS (
    SELECT bank_code
    FROM participants
    WHERE bank_code ~ '^BANK_[ABC]$'
      AND bank_name LIKE 'Bank _'
      AND status = 'ACTIVE'
      AND country = 'LA'
      AND currency = 'LAK'
)
DELETE FROM psp_certificates
WHERE cert_id LIKE 'seed-bank-%-mtls-cert'
   OR psp_id IN (SELECT bank_code FROM legacy_participants);

WITH legacy_participants AS (
    SELECT bank_code
    FROM participants
    WHERE bank_code ~ '^BANK_[ABC]$'
      AND bank_name LIKE 'Bank _'
      AND status = 'ACTIVE'
      AND country = 'LA'
      AND currency = 'LAK'
)
DELETE FROM oauth_clients
WHERE client_id LIKE 'client-bank-%'
   OR psp_id IN (SELECT bank_code FROM legacy_participants);

DELETE FROM api_keys
WHERE (role IN ('ADMIN', 'OPS') AND name IN ('Admin Key', 'Operations Key') AND key_prefix LIKE 'sk-%-swit%')
   OR (role = 'BANK' AND name LIKE 'Bank _ Key' AND key_prefix LIKE 'sk-bank-%-swit');

WITH legacy_vpa_registrations AS (
    SELECT vr.vpa_id
    FROM vpa_registrations vr
    JOIN participants p ON p.bank_code = vr.psp_id
    WHERE p.bank_code ~ '^BANK_[ABC]$'
      AND p.bank_name LIKE 'Bank _'
      AND p.status = 'ACTIVE'
      AND p.country = 'LA'
      AND p.currency = 'LAK'
)
DELETE FROM beneficiary_tokens
WHERE vpa_id IN (SELECT vpa_id FROM legacy_vpa_registrations);

WITH legacy_participants AS (
    SELECT bank_code
    FROM participants
    WHERE bank_code ~ '^BANK_[ABC]$'
      AND bank_name LIKE 'Bank _'
      AND status = 'ACTIVE'
      AND country = 'LA'
      AND currency = 'LAK'
)
DELETE FROM vpa_registrations
WHERE psp_id IN (SELECT bank_code FROM legacy_participants);

WITH legacy_webhook_registrations AS (
    SELECT wr.webhook_id
    FROM webhook_registrations wr
    JOIN participants p ON p.bank_code = wr.psp_id
    WHERE p.bank_code ~ '^BANK_[ABC]$'
      AND p.bank_name LIKE 'Bank _'
      AND p.status = 'ACTIVE'
      AND p.country = 'LA'
      AND p.currency = 'LAK'
)
DELETE FROM webhook_delivery_log
WHERE webhook_id IN (SELECT webhook_id FROM legacy_webhook_registrations);

WITH legacy_participants AS (
    SELECT bank_code
    FROM participants
    WHERE bank_code ~ '^BANK_[ABC]$'
      AND bank_name LIKE 'Bank _'
      AND status = 'ACTIVE'
      AND country = 'LA'
      AND currency = 'LAK'
)
DELETE FROM webhook_registrations
WHERE psp_id IN (SELECT bank_code FROM legacy_participants);

DELETE FROM routing_rules
WHERE route_code LIKE 'ROUTE_BANK_%_TO_BANK_%_PACS008';

DELETE FROM connector_credentials
WHERE connector_name LIKE 'MOCK_BANK_%_CONNECTOR';

DELETE FROM connector_rate_limits
WHERE connector_name LIKE 'MOCK_BANK_%_CONNECTOR';

DELETE FROM connector_configs
WHERE connector_name LIKE 'MOCK_BANK_%_CONNECTOR'
  AND connector_type = 'MOCK';

WITH legacy_participants AS (
    SELECT bank_code
    FROM participants
    WHERE bank_code ~ '^BANK_[ABC]$'
      AND bank_name LIKE 'Bank _'
      AND status = 'ACTIVE'
      AND country = 'LA'
      AND currency = 'LAK'
)
DELETE FROM settlement_instructions
WHERE debtor_psp_id IN (SELECT bank_code FROM legacy_participants)
   OR creditor_psp_id IN (SELECT bank_code FROM legacy_participants);

WITH legacy_participants AS (
    SELECT bank_code
    FROM participants
    WHERE bank_code ~ '^BANK_[ABC]$'
      AND bank_name LIKE 'Bank _'
      AND status = 'ACTIVE'
      AND country = 'LA'
      AND currency = 'LAK'
)
DELETE FROM participant_limits
WHERE bank_code IN (SELECT bank_code FROM legacy_participants);

WITH legacy_participants AS (
    SELECT bank_code
    FROM participants
    WHERE bank_code ~ '^BANK_[ABC]$'
      AND bank_name LIKE 'Bank _'
      AND status = 'ACTIVE'
      AND country = 'LA'
      AND currency = 'LAK'
)
DELETE FROM participant_certifications
WHERE bank_code IN (SELECT bank_code FROM legacy_participants);

WITH legacy_participants AS (
    SELECT bank_code
    FROM participants
    WHERE bank_code ~ '^BANK_[ABC]$'
      AND bank_name LIKE 'Bank _'
      AND status = 'ACTIVE'
      AND country = 'LA'
      AND currency = 'LAK'
)
DELETE FROM psp_suspension_log
WHERE psp_id IN (SELECT bank_code FROM legacy_participants);

WITH legacy_participants AS (
    SELECT bank_code
    FROM participants
    WHERE bank_code ~ '^BANK_[ABC]$'
      AND bank_name LIKE 'Bank _'
      AND status = 'ACTIVE'
      AND country = 'LA'
      AND currency = 'LAK'
)
DELETE FROM psp_pools
WHERE psp_id IN (SELECT bank_code FROM legacy_participants);

DELETE FROM participants
WHERE bank_code ~ '^BANK_[ABC]$'
  AND bank_name LIKE 'Bank _'
  AND status = 'ACTIVE'
  AND country = 'LA'
  AND currency = 'LAK';
