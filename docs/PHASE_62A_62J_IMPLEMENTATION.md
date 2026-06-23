# Phase 62A–62J Implementation

Phase 62 closes repository-side operational gaps identified after Phase 61. It
must not be used to claim UAT or production certification; runtime evidence is
still produced by Phase 61/54/55 runners.

| Phase | Implementation | Certification command |
|---|---|---|
| 62A | Regression guards for the four historical Maven blockers | `scripts/phase62/62A-test-blocker-regression.sh` |
| 62B | Strict static contract, fresh Surefire/Failsafe summary | `scripts/phase62/62B-full-verification-closure.sh` |
| 62C | SMOS permission matrix, OpenAPI, endpoint guard audit | `scripts/phase62/62C-smos-completion.sh` |
| 62D | Transaction-aware primary/replica routing | `scripts/phase62/62D-read-replica-routing.sh` |
| 62E | `NUMERIC(24,4)` money policy and V104 | `scripts/phase62/62E-financial-precision.sh` |
| 62F | HikariCP metrics, alerts, Grafana and runbook | `scripts/phase62/62F-hikari-monitoring.sh` |
| 62G | Critical dashboard RBAC, no-store, freshness and replica reads | `scripts/phase62/62G-dashboard-hardening.sh` |
| 62H | Promotion budget reservation and funder ledger controls | `scripts/phase62/62H-promotion-integrity.sh` |
| 62I | SQL fingerprint N+1 observation and bounded pagination | `scripts/phase62/62I-nplus1-pagination.sh` |
| 62J | OpenTelemetry export and durable trace correlation | `scripts/phase62/62J-distributed-tracing.sh` |

## Safety and consistency rules

- Financial writes, balance checks, idempotency, settlement transitions and
  immediate post-write reads remain on primary.
- Reporting/dashboard reads may use the replica and must expose freshness.
- Promotion remains disabled by default until Product approves launch and UAT
  reconciliation evidence passes.
- SQL fingerprints normalise literals and must never log payment payloads or PII.
- Trace attributes contain identifiers only; financial payloads are prohibited.
- V102 and V103 remain reserved. V104–V106 are forward-only migrations.

## Commands

```bash
# Static preparation only
scripts/phase62/run_phase62.sh --preflight

# Repository tests (requires Maven dependencies/Testcontainers)
scripts/phase62/run_phase62.sh --repo

# Strict aggregate contract
python3 scripts/verify_phase62_static.py
```

The strict aggregate contract intentionally fails if authoritative Phase II
migrations V91–V96 are missing. The delivery-only compatibility flag is not an
approval gate:

```bash
python3 scripts/verify_phase62_static.py --allow-missing-authoritative-phase-ii
```
