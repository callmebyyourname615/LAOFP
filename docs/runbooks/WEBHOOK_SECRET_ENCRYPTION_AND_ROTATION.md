# Runbook — Webhook Signing-Secret Encryption and Rotation

## Required Vault setup

Create a dedicated Transit key. Do not grant the application token broad Vault access.
The application and migration Job need only encrypt/decrypt access to this key.

Example policy shape:

```hcl
path "transit/encrypt/switching-webhook" {
  capabilities = ["update"]
}

path "transit/decrypt/switching-webhook" {
  capabilities = ["update"]
}
```

Required configuration:

```text
WEBHOOK_ENCRYPTION_PROVIDER=vault-transit
VAULT_ADDR=https://vault.example.internal
VAULT_TOKEN=<injected secret>
WEBHOOK_VAULT_TRANSIT_MOUNT=transit
WEBHOOK_VAULT_TRANSIT_KEY=switching-webhook
```

## First encrypted migration

1. Back up the database.
2. Confirm Vault health and the Transit policy using a non-production sample.
3. Deploy the immutable image to the registry.
4. Run `switching-db-migration` with Flyway credentials and Vault configuration.
5. Confirm the Job log includes `Successfully applied database migrations through version 44`.
6. Confirm the legacy column is absent:

```sql
SELECT column_name
FROM information_schema.columns
WHERE table_name = 'webhook_registrations'
  AND column_name = 'secret_plain';
```

Expected result: zero rows.

7. Confirm all encrypted fields are populated:

```sql
SELECT COUNT(*) AS incomplete
FROM webhook_registrations
WHERE secret_ciphertext IS NULL OR secret_key_id IS NULL;
```

Expected result: `0`.

8. Roll out the application using the exact same image digest.

Do not manually apply V44 before the Java backfill. V44 deliberately fails when
an unencrypted row exists.

## Rotate a PSP webhook secret

```http
POST /v1/webhooks/{webhookId}/secret/rotate
Content-Type: application/json

{
  "signingSecret": "<new secret of 32–256 characters>",
  "graceMinutes": 60
}
```

During the grace period, deliveries contain:

- `X-Webhook-Signature`: HMAC with the new/current secret.
- `X-Webhook-Signature-Previous`: HMAC with the previous secret.

The PSP should accept either valid signature during grace and remove the old
secret after `previousSecretExpiresAt`.

## Vault outage behavior

The service fails closed:

- No unsigned webhook is sent.
- No database hash or plaintext fallback is used.
- The delivery remains pending until normal retry limits are reached.

Operational response:

1. Check Vault health, TLS certificate, DNS, token expiry, and policy.
2. Restore Vault service or inject a renewed short-lived token.
3. Do not switch production to the local provider.
4. Observe pending webhook delivery count and retry processing.

## Key rotation

Vault Transit key rotation can occur without re-encrypting database rows because
Vault ciphertext contains its key version. Keep old Vault key versions available
for decrypt operations. Test decrypt of an existing ciphertext before removing or
archiving any Vault key material.

## Rollback warning

Application image rollback does not reverse database migrations. Any rollback
image used after V44 must understand `secret_ciphertext` and must not require
`secret_plain`.
