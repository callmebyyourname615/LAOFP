# RB 16 DATABASE LIFECYCLE

> Scope: Switching UAT/production operations. Never create a production fault only to test an alert.

## Common response workflow

1. Acknowledge the alert and record the incident/change reference.
2. Confirm the alert is not caused by monitoring-data loss or a planned maintenance window.
3. Freeze destructive or financially sensitive actions until impact is understood.
4. Preserve dashboards, logs, database/Kafka metrics and relevant audit records.
5. Apply the alert-specific mitigation below, then verify recovery and reconciliation.
6. Resolve the alert only after the underlying metric is healthy for its full hold period.

## Escalation and evidence

- Critical: page SRE and the owning Engineering/Operations lead immediately.
- Financial-integrity, security or sanctions impact: include Finance Control, Security or Compliance as applicable.
- Evidence: alert payload, start/end timestamps, query results, commands executed, approvals, reconciliation result and recovery screenshot.

## Invalid Index

### Alert: `SwitchingDatabaseInvalidIndex`

- Severity: `critical`
- Hold: `5m`
- Rule source: `monitoring/prometheus/database-lifecycle-rules.yaml`
- Summary: PostgreSQL has invalid indexes
- Description: PostgreSQL has invalid indexes

PromQL/expression:

```promql
max(switching_database_invalid_indexes) > 0
```

Immediate response:

1. Confirm `SwitchingDatabaseInvalidIndex` is active in Prometheus and Alertmanager, including labels and receiver.
2. Compare the current value with the last known healthy period and the current release/change timeline.
3. Check application health, pod/container restarts, PostgreSQL, Kafka/outbox and dependency status relevant to this rule.
4. If financial correctness may be affected, pause the related batch/cutover and run reconciliation before resuming.

Mitigation:

- Remove the immediate source of saturation, staleness, failure or policy violation using the approved change/runbook.
- Prefer rollback, traffic reduction, failover or queue draining over ad-hoc data edits.
- Require maker-checker approval for privileged or financial changes.

Recovery verification:

- Health endpoints are UP and the alert expression is below threshold.
- The alert transitions through Resolved and the expected recovery notification is delivered.
- Transaction, ledger, settlement and outbox reconciliation show zero unexplained difference when applicable.
- Attach the evidence to the incident/change record and document follow-up actions.

## Long Transaction Dead Tuples

### Alert: `SwitchingDatabaseLongTransaction`

- Severity: `warning`
- Hold: `10m`
- Rule source: `monitoring/prometheus/database-lifecycle-rules.yaml`
- Summary: Long transaction blocks vacuum or DDL
- Description: Long transaction blocks vacuum or DDL

PromQL/expression:

```promql
max(switching_database_long_transactions) > 0
```

### Alert: `SwitchingDatabaseDeadTuplePressure`

- Severity: `warning`
- Hold: `30m`
- Rule source: `monitoring/prometheus/database-lifecycle-rules.yaml`
- Summary: A table exceeds 20% dead tuples
- Description: A table exceeds 20% dead tuples

PromQL/expression:

```promql
max(switching_database_max_dead_tuple_ratio_basis_points) > 2000
```

Immediate response:

1. Confirm `SwitchingDatabaseLongTransaction`, `SwitchingDatabaseDeadTuplePressure` is active in Prometheus and Alertmanager, including labels and receiver.
2. Compare the current value with the last known healthy period and the current release/change timeline.
3. Check application health, pod/container restarts, PostgreSQL, Kafka/outbox and dependency status relevant to this rule.
4. If financial correctness may be affected, pause the related batch/cutover and run reconciliation before resuming.

Mitigation:

- Remove the immediate source of saturation, staleness, failure or policy violation using the approved change/runbook.
- Prefer rollback, traffic reduction, failover or queue draining over ad-hoc data edits.
- Require maker-checker approval for privileged or financial changes.

Recovery verification:

- Health endpoints are UP and the alert expression is below threshold.
- The alert transitions through Resolved and the expected recovery notification is delivered.
- Transaction, ledger, settlement and outbox reconciliation show zero unexplained difference when applicable.
- Attach the evidence to the incident/change record and document follow-up actions.

## Partition Gap

### Alert: `SwitchingDatabasePartitionHorizonGap`

- Severity: `critical`
- Hold: `5m`
- Rule source: `monitoring/prometheus/database-lifecycle-rules.yaml`
- Summary: Required seven-day partition horizon has gaps
- Description: Required seven-day partition horizon has gaps

PromQL/expression:

```promql
max(switching_database_partitions_missing_seven_days) > 0
```

Immediate response:

1. Confirm `SwitchingDatabasePartitionHorizonGap` is active in Prometheus and Alertmanager, including labels and receiver.
2. Compare the current value with the last known healthy period and the current release/change timeline.
3. Check application health, pod/container restarts, PostgreSQL, Kafka/outbox and dependency status relevant to this rule.
4. If financial correctness may be affected, pause the related batch/cutover and run reconciliation before resuming.

Mitigation:

- Remove the immediate source of saturation, staleness, failure or policy violation using the approved change/runbook.
- Prefer rollback, traffic reduction, failover or queue draining over ad-hoc data edits.
- Require maker-checker approval for privileged or financial changes.

Recovery verification:

- Health endpoints are UP and the alert expression is below threshold.
- The alert transitions through Resolved and the expected recovery notification is delivered.
- Transaction, ledger, settlement and outbox reconciliation show zero unexplained difference when applicable.
- Attach the evidence to the incident/change record and document follow-up actions.

## Maintenance Job

### Alert: `SwitchingDatabaseMaintenanceStale`

- Severity: `warning`
- Hold: `30m`
- Rule source: `monitoring/prometheus/database-lifecycle-rules.yaml`
- Summary: No successful database maintenance run for more than 8 days
- Description: No successful database maintenance run for more than 8 days

PromQL/expression:

```promql
max(switching_database_maintenance_last_success_age_seconds) > 691200
```

### Alert: `SwitchingDatabaseMaintenanceFailed`

- Severity: `critical`
- Hold: `5m`
- Rule source: `monitoring/prometheus/database-lifecycle-rules.yaml`
- Summary: Latest database maintenance run failed
- Description: Latest database maintenance run failed

PromQL/expression:

```promql
max(switching_database_maintenance_last_run_failed) > 0
```

Immediate response:

1. Confirm `SwitchingDatabaseMaintenanceStale`, `SwitchingDatabaseMaintenanceFailed` is active in Prometheus and Alertmanager, including labels and receiver.
2. Compare the current value with the last known healthy period and the current release/change timeline.
3. Check application health, pod/container restarts, PostgreSQL, Kafka/outbox and dependency status relevant to this rule.
4. If financial correctness may be affected, pause the related batch/cutover and run reconciliation before resuming.

Mitigation:

- Remove the immediate source of saturation, staleness, failure or policy violation using the approved change/runbook.
- Prefer rollback, traffic reduction, failover or queue draining over ad-hoc data edits.
- Require maker-checker approval for privileged or financial changes.

Recovery verification:

- Health endpoints are UP and the alert expression is below threshold.
- The alert transitions through Resolved and the expected recovery notification is delivered.
- Transaction, ledger, settlement and outbox reconciliation show zero unexplained difference when applicable.
- Attach the evidence to the incident/change record and document follow-up actions.

