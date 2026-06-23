# Hikari Pool Saturation Runbook

## Scope

Applies to `SwitchingPrimaryPool` and `SwitchingReadReplicaPool` alerts.

## Immediate checks

1. Confirm the affected `pool` label and current active/idle/pending metrics.
2. Check PostgreSQL `pg_stat_activity`, lock waits, deadlocks and replica lag.
3. Check application thread pools, request latency and recent deployment changes.
4. Do not increase pool size before confirming the database connection budget.

## Corrective actions

- Slow query: capture `pg_stat_statements`, add/repair index, or terminate only the approved runaway session.
- Lock contention: identify blocker and follow the database lock incident runbook.
- Replica outage: stop routing new reporting workloads to the replica; correctness-sensitive reads already remain on primary.
- Application leak: roll back the release and capture heap/thread evidence.
- Capacity shortage: scale application pods only if aggregate connection limits remain below the approved PostgreSQL budget.

## Recovery validation

- Pending threads return to zero.
- Utilization remains below 70% for 15 minutes.
- Connection-acquisition P95 is below 100 ms.
- Payment error rate and reconciliation remain within SLO.
- Record incident timeline, pool metrics and remediation in the evidence bundle.
