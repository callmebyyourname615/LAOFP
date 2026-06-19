# Switching Phases 43–52 — Implementation Detail

This increment continues the production-control roadmap after Phase 42. It adds database-enforced governance, runtime services, scheduled controls, alerts, runbooks, and CI acceptance checks. It does not replace external regulator, participant, security, or operations sign-off.

## Phase 43 — Transaction Limits and Product Entitlements

- Active product entitlement is required before limit evaluation.
- All matching system/product/participant policies are enforced; the most specific policy is evaluated first.
- Hourly and daily consumption uses policy time zones and serializable/advisory locking.
- Overrides are transaction-bound, amount-bound, time-limited, single-use, and independently approved.
- Every allow, deny, and override decision is hashed and retained.

## Phase 44 — Manual Financial Adjustments

- Adjustments contain immutable debit/credit lines and must balance before approval.
- Requester, approver, and executor must be different actors.
- Execution posts through the existing double-entry control ledger and is idempotent by adjustment reference.
- Approval/execution events are append-only evidence.

## Phase 45 — Settlement Calendar and Cutoff Governance

- Calendar versions, holidays, early closes, cycle cutoffs, grace periods, and late actions are governed.
- Only one active version exists per calendar code.
- Cutoff decisions store the business date, decision reason, and evidence hash.
- Changes require independent approval and bundle-hash verification.

## Phase 46 — Payment Finality and Duplicate Protection

- Idempotency keys are participant scoped and payload bound.
- Stable duplicate fingerprints prevent replay under a different idempotency key.
- Final records have immutable identity/evidence fields.
- Final-payment reversal requires separate Operations and Risk approvals plus an independent executor.

## Phase 47 — Cryptographic Asset Inventory and Rotation

- The inventory stores provider references and fingerprints, never key material.
- Rotation due dates, consumers, overlap windows, rollback references, and evidence are tracked.
- Requester cannot approve or execute their own rotation.
- Overdue, expiring, and failed rotations are monitored.

## Phase 48 — Third-Party Dependency SLA Governance

- Critical dependencies have active SLA/circuit policies and bounded HTTPS probes.
- Health samples are evidence hashed; circuit states are persisted.
- Forced-open/forced-closed changes are expiring, independently approved exceptions.
- The probe blocks redirects and private/reserved DNS results.

## Phase 49 — Capacity Forecast and Autoscaling Governance

- Observations retain workload, utilization, latency, error rate, replicas, and hashes.
- Forecasts store the source window, model version, confidence range, and required replicas.
- HPA policies are versioned and independently approved.
- Capacity changes require Operations and Performance approvals and a rollback reference.

## Phase 50 — Data Lineage and Evidence Catalog

- Governed assets record ownership, classification, retention linkage, and PII status.
- Lineage is versioned, approved, acyclic, and optionally field-mapping hashed.
- Evidence artifacts are safe-path checked, content hashed, size checked, verified, and sealed.
- Sealed artifact identity is database immutable.

## Phase 51 — Fraud/AML Decision Rule Governance

- Rule artifacts and manifests are immutable SHA-256 referenced versions.
- Activation requires a passing test suite and independent Risk and Compliance approvals.
- Deployment is environment referenced and records the previous version for rollback.
- Rollback restores the previous approved version and leaves deployment evidence.

## Phase 52 — Controlled Decommissioning and Data Exit

- Participant, connector, product, service, and dataset retirement use a controlled plan.
- Operations, Risk, and Business approvals are required from distinct actors.
- Blocking tasks must complete; required data-exit artifacts must be encrypted.
- Participant decommissioning is blocked while unsettled transactions remain.

## Shared acceptance

1. Flyway V73–V82 applies on empty and upgraded PostgreSQL databases.
2. `./mvnw clean verify` passes with focused and full suites.
3. Static control verification and negative validator fixtures pass.
4. CronJobs use digest-pinned operations images in deployment overlays.
5. Prometheus metrics and alerts are simulated in UAT.
6. Evidence hashes, role separation, expiry, rollback, and fail-closed behavior are demonstrated.
