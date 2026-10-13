# Phases 05 To 07 Implementation Closure

## Scope

This document records the implementation closure for phases 05 through 07 and ties the static production gates to the operational runbooks that must exist before go-live.

## Phase 05 - Sanctions Provider Import

Implemented production behavior:

- Provider imports write through staging before live promotion.
- Provider UID uniqueness is enforced by migration `V45__sanctions_provider_import.sql`.
- Parser fixtures cover OFAC XML, UN XML, and Bank of Laos FIU JSON samples.
- Failed sync keeps last-known-good sanctions data available.
- Startup validation blocks production when sanctions data is stale or provider import has not succeeded.

Operational closure:

- Provider onboarding and production evidence requirements are documented in `docs/aml/SANCTIONS_PROVIDER_ONBOARDING.md`.
- Production enablement requires a completed provider import and AML sign-off.

## Phase 06 - External Secret Boundaries And Transport Hardening

Implemented production behavior:

- External Secrets resources use `external-secrets.io/v1`.
- Raw Kubernetes Secret data is removed from `k8s/secret.yaml`.
- Application and migration secret boundaries are split.
- Production Kafka, database, and Vault configuration enforce TLS-oriented transport settings.
- Production Vault auth defaults to Kubernetes auth instead of static tokens.

Operational closure:

- Bootstrap, migration, rotation, and verification steps are documented in `docs/security/VAULT_EXTERNAL_SECRETS_RUNBOOK.md`.
- `scripts/bootstrap_vault_kubernetes_auth.sh` is the approved auth bootstrap entry point.
- `scripts/migrate_to_external_secrets.sh` is the approved migration entry point for legacy Kubernetes Secret environments.

## Phase 07 - Monitoring And Runtime Evidence

Implemented production behavior:

- Monitoring dashboards are checked as static artifacts.
- Operational runbooks exist for API SLO, database and queue pressure, transaction settlement, webhook, AML, sanctions, and STR workflows.
- Shell, YAML, and dashboard JSON syntax are included in static verification.

Operational closure:

- Runtime evidence remains separate from static documentation closure.
- UAT and production runtime drills must still attach real environment output before final go-live approval.
- Static closure is necessary for release readiness, but it does not replace live smoke, transfer, inquiry, settlement, AML, or rollback evidence.

## Verification

Phase 92 closes the documentation blocker by requiring these artifacts:

- `docs/aml/SANCTIONS_PROVIDER_ONBOARDING.md`
- `docs/security/VAULT_EXTERNAL_SECRETS_RUNBOOK.md`
- `docs/implementation/PHASES_05_TO_07_IMPLEMENTATION.md`

Run the focused static gates:

```bash
python3 scripts/verify_phases_05_07_static.py
python3 scripts/verify_phases_53c_53j.py
```

Run the full static gate before release candidate tagging:

```bash
python3 scripts/verify_all_static.py
```

## Production Impact

These phases do not require a database migration during documentation closure. They do require operators to complete provider import evidence, Vault External Secrets evidence, and runtime go-live evidence before production traffic is enabled.
