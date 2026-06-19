# Phases 05–07 Implementation Record

## Phase 05 — Sanctions provider safety

Implemented bounded HTTPS retrieval, secure XML parsing, provider adapters, staging import, advisory locking, atomic activation, soft-delete, rejected-record audit, freshness health/metrics and last-known-good behavior.

## Phase 06 — Secret and transport boundary

Implemented Vault Kubernetes authentication, ExternalSecret resources with retained lifecycle, separation of application/Flyway identities, PostgreSQL verify-full, Kafka SASL/SSL with hostname verification, and production configuration validation.

## Phase 07 — Operational observability

Implemented private management Service/ServiceMonitor, operational database gauges, four Grafana dashboards, eleven core alerts and RB-08 through RB-11. The collector is runtime-enabled by default and excluded from the migration process.

## Verification

Run:

```bash
python3 scripts/verify_phases_05_07_static.py
./mvnw --batch-mode --no-transfer-progress test
```

Before production, execute controlled alert firing tests and attach the resulting evidence bundle.
