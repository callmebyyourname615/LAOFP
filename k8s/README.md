# Kubernetes Manifests

These manifests are production-hardening templates, not deploy-ready cluster configuration.

## Required rendering

Before applying them to a real cluster:

- Replace every `REPLACE_WITH_...` value in `configmap.yaml`, image manifests, and External Secrets templates.
- Replace `switching.example.com` in `ingress.yaml` with the real API host.
- Adjust NetworkPolicy namespaces/CIDRs for the actual DB, Kafka, Vault, object storage, payment networks, DNS, ingress, and monitoring stack.
- Keep `k8s/secret.yaml` as the empty guard manifest. Never restore a raw Kubernetes Secret to Git.
- Run `scripts/check_prod_config.sh` against the rendered environment/manifests. Template placeholders are expected to fail.
- Run `kubectl apply --dry-run=server` against the target cluster before rollout.

The production profile independently rejects placeholders, localhost/mock endpoints, weak DB/Kafka TLS, static Vault tokens, stale/empty sanctions data, and incomplete webhook-secret encryption.

## External Secrets and Vault order

1. Install External Secrets Operator and confirm its CRDs/controller are Ready.
2. Populate Vault KV v2 paths described in `k8s/external-secrets/*.yaml`.
3. Configure Vault Kubernetes auth with `scripts/bootstrap_vault_kubernetes_auth.sh`.
4. Apply `k8s/external-secrets/vault-secret-store.yaml`.
5. Apply application, migration, and trust-bundle ExternalSecrets.
6. Run `scripts/wait_for_external_secrets.sh` and verify generated Secret ownership labels.
7. Do not deploy if any ExternalSecret is not Ready.

See `docs/security/VAULT_EXTERNAL_SECRETS_RUNBOOK.md`.

## Migration and immutable-image deployment

Do not apply `deployment.yaml` directly with its placeholder image. Render both the migration Job and Deployment with the same registry digest using `scripts/render_k8s_image.sh`, or use the immutable-image GitHub Actions workflow.

Required order:

1. wait for External Secrets
2. apply/wait for `migration-job.yaml`
3. preserve Flyway/backfill logs
4. apply `deployment.yaml` using the identical digest
5. wait for rollout and verify the running image digest
6. verify readiness, Prometheus target, and alert rules

See `docs/runbooks/KUBERNETES_FLYWAY_MIGRATION_JOB.md` and `docs/runbooks/RB-06-emergency-rollback.md`.

## Monitoring

Apply the ServiceMonitor and PrometheusRule under `monitoring/prometheus`. The management service is internal and selected using `monitoring: enabled`; NetworkPolicy only permits port 9090 from the `monitoring` namespace.

## Backup, WAL archive and PITR

Phase 8 resources live under `k8s/backup/`. Build the separate `switching-backup` image, render every image-bearing manifest with its immutable digest, and apply the backup ExternalSecrets first.

The restore drill CronJob is suspended by default. Run and approve one manual isolated restore before unsuspending the monthly schedule. Never mount `switching-restore-secrets` into the full-backup or WAL-archiver workloads.

See `docs/runbooks/RB-12_BACKUP_PITR_AND_RESTORE.md`.
