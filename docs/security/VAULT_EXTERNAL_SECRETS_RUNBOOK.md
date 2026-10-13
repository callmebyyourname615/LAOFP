# Vault External Secrets Runbook

## Scope

This runbook defines the production secret boundary for the switching service. Production secrets must be sourced from Vault through External Secrets and Kubernetes auth. Static Kubernetes Secret manifests and static Vault token properties are not production-approved.

## Production Requirements

- Vault address must use HTTPS in production.
- Application pods authenticate to Vault with Kubernetes auth.
- `k8s/secret.yaml` must remain an empty compatibility list and must not contain raw Secret data.
- Application and migration secret scopes stay separate.
- ExternalSecret resources use `creationPolicy: Orphan` and `deletionPolicy: Retain`.
- Vault token values must not be committed, rendered into ConfigMaps, or configured in `application-prod.yml`.

## Bootstrap Flow

1. Configure Vault Kubernetes auth for the target cluster:

```bash
./scripts/bootstrap_vault_kubernetes_auth.sh
```

2. Load production values into Vault using the approved secret path naming convention.
3. Apply External Secrets resources:

```bash
kubectl apply -f k8s/external-secrets/vault-secret-store.yaml
kubectl apply -f k8s/external-secrets/application-secrets.yaml
kubectl apply -f k8s/external-secrets/migration-secrets.yaml
kubectl apply -f k8s/external-secrets/trust-bundle.yaml
```

4. Migrate away from local Kubernetes Secrets when replacing a legacy environment:

```bash
./scripts/migrate_to_external_secrets.sh
```

## Runtime Verification

Run these checks after deployment:

```bash
kubectl get secretstore,externalsecret
kubectl describe externalsecret switching-application-secrets
kubectl describe externalsecret switching-migration-secrets
kubectl logs deploy/external-secrets -n external-secrets --tail=100
```

Expected result:

- SecretStore is ready.
- ExternalSecret status is ready and refreshed.
- Application secret does not contain Flyway-only credentials.
- Migration secret does not contain application runtime credentials.
- External Secrets controller logs do not show Vault auth, TLS, or permission errors.

## Rotation Procedure

- Stage the new secret value in Vault.
- Confirm External Secrets refresh interval and controller health.
- Trigger or wait for ExternalSecret refresh.
- Restart only workloads that do not support live reload.
- Run smoke checks for inquiry, transfer, webhook crypto, database, and Kafka connectivity.
- Record old and new Vault version numbers in the change ticket.

## Incident Response

If Vault or External Secrets is degraded:

- Do not apply raw Kubernetes Secret manifests as a shortcut.
- Freeze deployments that require new or rotated secrets.
- Confirm whether existing Kubernetes Secret objects are still present from the last successful sync.
- Restore Vault availability or controller permissions first.
- Resume rollout only after ExternalSecret status returns to ready.

## Evidence

Attach the following to the production release ticket:

- Output of `kubectl get secretstore,externalsecret`.
- Redacted ExternalSecret describe output.
- Vault Kubernetes auth bootstrap command result.
- Rotation or initial secret load approval.
- SecOps confirmation that Vault TLS and Kubernetes auth are enabled.
