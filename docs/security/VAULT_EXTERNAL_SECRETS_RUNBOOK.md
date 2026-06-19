# Vault and External Secrets Runbook

## Bootstrap order

1. Install External Secrets Operator CRDs/controller.
2. Create Vault KV v2 values for application, migration, and trust material.
3. Enable/configure Vault Kubernetes auth with `scripts/bootstrap_vault_kubernetes_auth.sh`.
4. Apply `vault-secret-store.yaml`.
5. Apply the three ExternalSecrets.
6. Run `scripts/wait_for_external_secrets.sh`.
7. Run `scripts/check_prod_config.sh` against the rendered production configuration.
8. Run the migration Job, then roll out the application.

## Rotation

- Rotate one credential class at a time.
- Write the new value to Vault; do not edit the generated Kubernetes Secret.
- Wait until the ExternalSecret is `Ready` and its refresh timestamp advances.
- Restart only workloads that do not reload the credential dynamically.
- Verify DB/Kafka/Vault connectivity and retain the previous credential only for the approved overlap window.
- Revoke the previous credential and attach evidence to the change record.

## Emergency controls

- A Vault/ESO outage must not delete generated Secrets (`deletionPolicy: Retain`).
- Do not set `VAULT_TOKEN` in production.
- Do not grant the application Transit key-management/export permissions.
- If a generated Secret has the wrong owner/content, stop rollout and restore from Vault version history; never copy values into Git.

## Backup and restore paths

Phase 8 adds two separate Vault paths:

- `kv/switching/prod/backup`: replication username/password, primary/secondary object-storage credentials, and the public age recipient;
- `kv/switching/prod/restore`: the private `BACKUP_AGE_IDENTITY` only.

The backup and WAL workloads must never receive the restore identity. Only isolated restore jobs mount `switching-restore-secrets`, as a read-only file. Audit Vault reads of the restore path and alert on access outside an approved drill or incident window.
