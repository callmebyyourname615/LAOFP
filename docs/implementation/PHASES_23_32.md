# Switching API — Phase 23–32 Implementation Notes

This package continues after Phase 13–22 and adds ten operational maturity phases. It is designed as a changed-files overlay and should be applied only after the assembled Phase 1–22 baseline.

## Phase 23 — Cross-System Reconciliation Automation

Adds control-run and exception-case tables, daily reconciliation scripts, and a Kubernetes CronJob. A reconciliation run is not successful until transaction counts, settlement counts, amounts, checksum files, and evidence JSON are produced.

## Phase 24 — Fraud and Velocity Controls

Adds configurable velocity rules and durable fraud decisions. The rule engine persists evidence hashes and supports ALLOW, REVIEW, HOLD, and REJECT. Rules are loaded through a CSV validation gate.

## Phase 25 — Participant Lifecycle SLA

Adds lifecycle cases, contact registry, approval constraints, and an activation gate. Approval by the same requester is blocked at service level and must also be enforced by the operational procedure.

## Phase 26 — ISO Message Validation Packs

Adds versioned validation packs, canonical payload hashing, and result persistence. Validation packs must be activated only after hash verification and compatibility review.

## Phase 27 — Settlement Evidence Ledger

Adds chained settlement evidence records and dispute-evidence linkage. Each evidence record includes source hash, previous chain hash, and current chain hash.

## Phase 28 — Operations Command Center

Adds daily control-room records, task tracking, opening/closing evidence, and an operational readiness service.

## Phase 29 — Data Quality Controls

Adds data quality rule definitions, scheduled execution, sample placeholders, and failure metrics for critical data checks.

## Phase 30 — Multi-Region Readiness

Adds region readiness probes and failover-candidate tracking. Region promotion remains manual and cannot pass unless recent probes show acceptable health and replication lag.

## Phase 31 — Privacy and PII Case Management

Adds privacy access cases and PII discovery result tracking. Exports are evidence-hashed and should be reviewed for redaction before release.

## Phase 32 — Continuous Compliance Controls

Adds control definitions, scheduled control runs, evidence hash capture, and a CI workflow that packages control evidence.

## Deployment Order

1. Apply Flyway V53–V62 in staging.
2. Deploy application changes.
3. Deploy CronJobs with digest-pinned ops images.
4. Seed control definitions and data quality rules.
5. Run static verifier: `python3 scripts/verify_phases_23_32_static.py`.
6. Run first reconciliation, data-quality, region-readiness, and compliance jobs manually.
7. Archive generated evidence and attach to operations readiness review.
