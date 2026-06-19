# Production Environment Contract

## Source of truth

`config/production-environment-contract.yaml` is the machine-readable contract. `.env.prod.example` is documentation only and must never contain real credentials. Kubernetes delivery is split between `k8s/configmap.yaml`, `k8s/external-secrets/application-secrets.yaml`, and `k8s/external-secrets/migration-secrets.yaml`.

## Required checks

```bash
python3 scripts/validate_production_environment.py \
  --env-file .env.prod.example --template
python3 scripts/validate_production_environment.py --verify-k8s
```

In a secure pre-deployment job, render secret values into process environment without writing them to disk and run:

```bash
python3 scripts/validate_production_environment.py --from-environment
```

The validator rejects placeholders, localhost/mock endpoints, non-TLS service URLs, weak secrets, invalid cryptographic material, shared app/Flyway credentials, wrong booleans/literals, missing variables and forbidden static Vault tokens.

## Separation of duties

- Application DB user: DML only; no schema ownership or DDL.
- Flyway DB user: migration-only identity delivered exclusively to the migration Job.
- Application secrets: mounted through External Secrets; never ConfigMap.
- Vault: Kubernetes auth with scoped role; no `VAULT_TOKEN` in the application environment.
- Production startup: `ProductionStartupValidator` independently rejects unsafe runtime values.

## Change control

Any contract key addition/removal requires application configuration changes, Kubernetes delivery updates, validator regression tests, security review and a release note. A rendered environment that does not pass the contract is a deployment blocker.
