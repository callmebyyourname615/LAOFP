# Switching — Phase 62A–62J Implementation Report

**Date:** 2026-06-23  
**Scope:** Repository-side implementation for Phase 62A–62J  
**Baseline:** User-supplied Switching source after Phase 61  
**Certification statement:** Repository implementation is complete. Maven, migration-chain and UAT runtime certification are not claimed as passed.

## Executive status

| Phase | Repository implementation | Current certification state |
|---|---|---|
| 62A Remaining Maven blockers | Implemented | PREPARED — regression guards pass |
| 62B Full verification closure | Implemented | PREPARED — Maven unavailable in sandbox |
| 62C SMOS completion | Implemented | PREPARED — UAT bootstrap/security acceptance pending |
| 62D Read-replica routing | Implemented | PREPARED — primary/replica UAT proof pending |
| 62E Financial precision | Implemented | BLOCKED for strict migration certification until authoritative V91–V96 are restored |
| 62F HikariCP monitoring | Implemented | PASS static / runtime alert firing pending |
| 62G Dashboard hardening | Implemented | PREPARED — UAT RBAC/data/performance acceptance pending |
| 62H Promotion financial integrity | Implemented | PREPARED — feature remains disabled by default |
| 62I N+1 and pagination | Implemented | PREPARED — controlled UAT observation pending |
| 62J Distributed tracing | Implemented | PREPARED — OTLP backend/end-to-end trace proof pending |

## 62A — Remaining Maven blocker closure

The four checklist items were already fixed in the supplied source. Phase 62 adds static regression guards so they cannot silently regress:

- Migration/webhook encryption configuration has an `ObjectMapper`.
- Sanctions integration fixtures populate `provider_uid`.
- Operations integration cleanup removes child suspension records before participants.
- Cross-border timestamp binding uses an explicit PostgreSQL timestamp type.

Added:

- `scripts/phase62/verify_test_blocker_fixes.py`
- `scripts/phase62/62A-test-blocker-regression.sh`

## 62B — Full build and verification closure

Added:

- Fresh Surefire/Failsafe report certification.
- Stale-report rejection.
- Machine-readable Phase 62 results.
- Aggregate Phase 62 static verifier.
- Phase 62 CI workflow.
- Phase 62 preflight integration in `scripts/execute-and-verify/00-run-all.sh`.

Strict validation expects 99 migrations through V106. The supplied baseline contains 93 because V91–V96 are absent. Delivery mode validates this package but does not waive the production gate.

## 62C — SMOS completion

Added/hardened:

- Full RBAC permission matrix.
- OpenAPI contract for `/api/auth/*` and `/api/admin/users/*`.
- Static audit for admin endpoint authorization.
- Explicit one-time bootstrap-admin acknowledgement.
- Bounded user pagination with maximum page size 100.
- `@EntityGraph` loading for user roles.
- Participant-scoped user administration and session revocation on security changes.

## 62D — Read-replica routing

Implemented:

- Primary and read-replica HikariCP pools.
- Transaction-aware `AbstractRoutingDataSource`.
- `LazyConnectionDataSourceProxy` so routing is decided after transaction metadata is available.
- Primary consistency override for financial/write-sensitive paths.
- Dedicated replica-backed reporting `JdbcTemplate`.
- UAT certification script using `pg_is_in_recovery()`.
- Read-your-writes and failover policy documentation.

Financial writes, balance checks, idempotency decisions, settlement transitions and immediate post-write reads remain on primary.

## 62E — Financial precision standardisation

Added migration:

- `V104__standardize_financial_numeric_precision.sql`

Implemented:

- Money standard `NUMERIC(24,4)`.
- Separate FX-rate precision `NUMERIC(24,10)`.
- Domain precision policy table.
- Java `MoneyPrecisionPolicy` with rounding/scale tests.
- Migration integration contract.

This is a forward-only migration and must be rehearsed against a production-like copy before approval.

## 62F — HikariCP monitoring

Added:

- Primary and replica pool metrics.
- Grafana database-pool dashboard.
- Prometheus alerts for utilization, pending threads, acquisition latency and replica availability.
- Hikari pool saturation runbook.

Runtime alert firing and routing still require UAT execution.

## 62G — Dashboard hardening

Hardened Settlement, Risk and Cross-Border dashboards with:

- Role and participant-scope enforcement.
- Scheme-wide dashboard denial for participant-scoped operators.
- Read-replica routing.
- Read-only transactions and database statement timeout.
- Bounded result sets.
- `Cache-Control: no-store`.
- Data-freshness response headers.
- Canonical `/api/dashboard/cross-border` path with legacy compatibility.

## 62H — Promotion financial integrity

Added migration:

- `V105__promotion_budget_and_funder_ledger_controls.sql`

Implemented:

- Budget accounts.
- Idempotent reservations.
- Advisory-lock protected budget allocation.
- Consume, release, refund and expiry flows.
- Immutable funder-ledger events.
- Concurrent budget-cap protection.
- Integration tests for idempotency, conflicts, cap exhaustion, refund and expiry.

Promotion remains disabled by default until Product approval and UAT financial reconciliation pass.

## 62I — N+1 detection and pagination

Implemented:

- SQL fingerprinting that normalises literals.
- Request-scoped query-frequency detection.
- Metrics and warnings without logging payment payloads or PII.
- Controlled UAT enablement; disabled in production by contract.
- Bounded SMOS user-list pagination.
- Query-count and fingerprint unit tests.

## 62J — Distributed tracing

Added migration:

- `V106__distributed_trace_correlation.sql`

Implemented:

- Micrometer tracing bridge for OpenTelemetry.
- OTLP exporter configuration.
- Kafka observation enablement.
- Trace propagation through outbox, Kafka messages, transaction events and audit logs.
- Durable `trace_id` columns and indexes.
- API error trace IDs.
- Trace IDs included in audit-chain hashing.
- Guard that only valid 32-character hexadecimal trace IDs are persisted.

Financial payloads and PII are prohibited from span attributes.

## Validation performed

Passed:

- All nine Phase 62 component static verifiers.
- Phase 62 delivery static contract.
- Phase 62 preflight in delivery-baseline mode.
- Bash syntax checks.
- Python syntax checks.
- YAML and JSON parsing.
- Git whitespace checks.
- Java lexical/brace scan for 47 changed Java files.
- Standalone `javac` compilation for pure Java Phase 62 classes.
- Known exposed-secret value scan.

Strict gate result:

```text
Phase 62 static contract: FAIL
migration count: 93, expected 99
missing authoritative migrations: V91–V96
```

This failure is intentional and must not be bypassed for RC/UAT certification. Merge V91–V96 from the authoritative Phase II branch/package.

Maven attempt:

```text
wget: Failed to fetch Maven 3.9.12 from Maven Central
exit code: 1
```

Therefore this report does not claim `./mvnw clean verify` passed.

## Required next execution

1. Apply the changed-files package to the authoritative repository that contains V91–V96.
2. Remove `.env.bak` and `new.txt` if still tracked.
3. Run strict static validation:

```bash
python3 scripts/verify_phase62_static.py
```

4. Run Maven and repository certification:

```bash
./mvnw clean verify
scripts/phase62/run_phase62.sh --repo
./scripts/execute-and-verify/00-run-all.sh
```

5. Run UAT certifications for replica routing, dashboards, promotion controls, pool alerts, N+1 observation and OTLP tracing.
6. Continue Phase 61 runtime evidence, Phase 54 UAT certification and Phase 55 production cutover only after strict gates pass.
