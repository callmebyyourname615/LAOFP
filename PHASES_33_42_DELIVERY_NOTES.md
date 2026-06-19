# Phase 33–42 Delivery Notes

## Scope

This changed-files overlay continues after the assembled Phase 1–32 baseline and adds:

1. Phase 33 — Double-entry control ledger
2. Phase 34 — Intraday liquidity and prefunding controls
3. Phase 35 — Tariff and fee governance
4. Phase 36 — FX rate governance
5. Phase 37 — Participant certificate lifecycle
6. Phase 38 — Regulatory reporting and submission evidence
7. Phase 39 — Notification delivery governance
8. Phase 40 — Change freeze and release calendar
9. Phase 41 — Synthetic transaction monitoring
10. Phase 42 — Incident and corrective/preventive action management

## Apply Order

1. Assemble Phase 1–32.
2. Apply this overlay.
3. Run Flyway V63–V72 on staging.
4. Run `./mvnw clean verify`.
5. Seed only approved control data; do not enable tariffs, providers, templates, probes, or certificates directly in SQL.
6. Deploy CronJobs and Prometheus rules with digest-pinned operational images.

## Critical Gates

- A posted journal must contain at least two lines and equal debit/credit totals.
- Liquidity reservation and settlement must be atomic and preserve minimum balances.
- Tariffs and FX publications require versioning, hashes, freshness, and independent approval.
- Certificate records store metadata/fingerprints only; private keys are never stored in the application database.
- Regulatory report generation, validation, and submission are segregated duties.
- Notification recipients are stored as pseudonymous hashes and retries are bounded.
- Production release requires a recent ALLOW decision inside a valid window.
- Synthetic probes may only use reserved SYN participants and references.
- Incident closure requires root cause, completed/risk-accepted actions, and three approval roles.

## Sandbox Validation

- Phase 33–42 static acceptance script
- YAML multi-document parsing
- Shell syntax and Python compilation
- Migration/control invariant checks
- Tariff-bundle validation harness

## Required UAT Evidence

- Full Maven verification and Testcontainers
- Flyway V63–V72 on a copy of staging data
- Journal balance trigger and immutability tests
- Concurrent liquidity reservation test
- Tariff and FX four-eyes approval drill
- Certificate overlap/revocation drill with non-production certificates
- Encrypted regulatory report submission dry run
- Notification retry/dead-letter simulation
- Freeze-window deny/exception/allow drill, including the production deploy workflow gate
- Scheduled synthetic inquiry/end-to-end transaction using isolated SYN participants and database evidence
- SEV1 tabletop exercise through RCA and CAPA closure
