# Phase 56 Day-2 Operations

## Purpose

This runbook governs recurring production controls after go-live. Repository implementation does not itself authorize a production action. All executions require immutable release identity, protected runners, least-privilege credentials and retained evidence.

## Execution order

1. 56A SLO/error-budget gate.
2. 56B continuous reconciliation.
3. 56C HA/failover certification.
4. 56D capacity/autoscaling validation.
5. 56E security-event detection review.
6. 56F compliance evidence collection.
7. 56G progressive-delivery analysis.
8. 56H incident lifecycle audit.
9. 56I cost and storage forecast.
10. 56J resilience certificate.

## Mandatory safety controls

- Never run with mutable image tags.
- Production commands require `PRODUCTION_EXECUTION_CONFIRMATION`.
- Database reconciliation uses read-only repeatable-read and advisory locking.
- Database primary promotion remains human-approved and fenced.
- Autoscaling may not exceed database connection or Kafka partition budgets.
- Security detection never performs destructive automatic containment.
- Evidence must be hashed, signed where required, and archived with Object Lock.
- A failed phase must never be converted to PASS manually.

## Evidence

Evidence is written under `build/phase56-day2/phases/<phase>`. Archive the directory to the compliance bucket and record the Object Lock receipt.

## Abort criteria

Abort and open an incident for data mismatch, duplicate replay, split-brain indication, unclean Kafka leader election, expired signing identity, critical threat detection, RPO/RTO breach, or evidence tampering.

## Kubernetes scheduling

The default Day-2 overlay enables SLO and read-only reconciliation schedules. Compliance and FinOps CronJobs are delivered as templates but are excluded from the default kustomization until the organization-specific evidence-store and cloud-billing adapters are configured.

## Immutable evidence store

Protected runners must use `scripts/day2/evidence_store.sh pull` before a Phase 56 run and
`push` afterward. The evidence bucket must have versioning and default Object Lock in
`COMPLIANCE` mode. Uploads are restricted to the Phase 54, 55, 56, and Phase 56 input
prefixes under `build/`, use SSE-KMS, reject symlinks, and run the runtime evidence secret
scanner before synchronization.

Phase 56F intentionally excludes the current release's Phase 56J certificate from its
pre-certificate control set. After 56J succeeds, evaluate
`compliance/post-resilience-controls.yaml` with
`compliance/post-resilience-evidence-mapping.yaml`; this avoids a circular dependency while
still producing the monthly resilience compliance record.
