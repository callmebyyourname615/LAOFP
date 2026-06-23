# RB 12 BACKUP PITR AND RESTORE

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

## Switchingbasebackupstale

### Alert: `SwitchingBaseBackupStale`

- Severity: `critical`
- Hold: `10m`
- Rule source: `monitoring/prometheus/backup-rules.yaml`
- Summary: No successful physical base backup in 30 hours
- Description: The production backup recovery chain may no longer meet its RPO/RTO assumptions.

PromQL/expression:

```promql
absent(switching_backup_last_success_timestamp_seconds) or time() - max(switching_backup_last_success_timestamp_seconds) > 108000
```

Immediate response:

1. Confirm `SwitchingBaseBackupStale` is active in Prometheus and Alertmanager, including labels and receiver.
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

## Switchingbasebackupfailed

### Alert: `SwitchingBaseBackupFailed`

- Severity: `critical`
- Hold: `5m`
- Rule source: `monitoring/prometheus/backup-rules.yaml`
- Summary: The latest physical base backup attempt failed
- Description: Review the CronJob, PostgreSQL replication connection, encryption, and both object-storage targets.

PromQL/expression:

```promql
max(switching_backup_last_attempt_failed) > 0
```

Immediate response:

1. Confirm `SwitchingBaseBackupFailed` is active in Prometheus and Alertmanager, including labels and receiver.
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

## Switchingwalarchivestale

### Alert: `SwitchingWalArchiveStale`

- Severity: `critical`
- Hold: `5m`
- Rule source: `monitoring/prometheus/backup-rules.yaml`
- Summary: Continuous WAL archive has not advanced for 15 minutes
- Description: PITR data-loss exposure is increasing. Check pg_receivewal, the physical slot, spool capacity, and object storage.

PromQL/expression:

```promql
absent(switching_wal_archive_last_success_timestamp_seconds) or time() - max(switching_wal_archive_last_success_timestamp_seconds) > 900
```

Immediate response:

1. Confirm `SwitchingWalArchiveStale` is active in Prometheus and Alertmanager, including labels and receiver.
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

## Switchingwalarchivefailed

### Alert: `SwitchingWalArchiveFailed`

- Severity: `critical`
- Hold: `5m`
- Rule source: `monitoring/prometheus/backup-rules.yaml`
- Summary: A WAL segment failed encryption or upload
- Description: The segment remains in the durable spool and must be uploaded before it is removed.

PromQL/expression:

```promql
max(switching_wal_archive_failed) > 0
```

Immediate response:

1. Confirm `SwitchingWalArchiveFailed` is active in Prometheus and Alertmanager, including labels and receiver.
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

## Switchingbackupcrossregioncopyfailed

### Alert: `SwitchingBackupCrossRegionCopyFailed`

- Severity: `critical`
- Hold: `15m`
- Rule source: `monitoring/prometheus/backup-rules.yaml`
- Summary: Off-site backup copy is absent or no longer verified
- Description: Primary storage loss could remove the only recoverable copy.

PromQL/expression:

```promql
max(switching_backup_cross_region_success) < 1 or time() - max(switching_backup_cross_region_last_check_timestamp_seconds) > 108000
```

Immediate response:

1. Confirm `SwitchingBackupCrossRegionCopyFailed` is active in Prometheus and Alertmanager, including labels and receiver.
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

## Switchingrestoredrilloverdue

### Alert: `SwitchingRestoreDrillOverdue`

- Severity: `warning`
- Hold: `1h`
- Rule source: `monitoring/prometheus/backup-rules.yaml`
- Summary: No successful isolated restore drill in 35 days
- Description: A backup is not considered recoverable until a current restore drill has passed.

PromQL/expression:

```promql
absent(switching_restore_drill_last_success_timestamp_seconds) or time() - max(switching_restore_drill_last_success_timestamp_seconds) > 3024000
```

Immediate response:

1. Confirm `SwitchingRestoreDrillOverdue` is active in Prometheus and Alertmanager, including labels and receiver.
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

## Switchingrestoredrillfailedormissedrto

### Alert: `SwitchingRestoreDrillFailedOrMissedRto`

- Severity: `critical`
- Hold: `5m`
- Rule source: `monitoring/prometheus/backup-rules.yaml`
- Summary: Restore drill failed or exceeded the approved RTO
- Description: Keep the production go-live gate closed until the failure is remediated and the drill is repeated.

PromQL/expression:

```promql
max(switching_restore_drill_failed) > 0 or max(switching_restore_drill_rto_target_met) < 1
```

Immediate response:

1. Confirm `SwitchingRestoreDrillFailedOrMissedRto` is active in Prometheus and Alertmanager, including labels and receiver.
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

## Switching Backup Restore Certification Stale

### Alert: `SwitchingBackupRestoreCertificationStale`

- Severity: `warning`
- Hold: `1h`
- Rule source: `monitoring/prometheus/phase56-resilience-rules.yaml`
- Summary: Restore certification older than 31 days
- Description: No successful restore drill has been recorded within the required 31-day certification interval.

PromQL/expression:

```promql
switching_restore_drill_last_success_timestamp_seconds < time() - 2678400
```

Immediate response:

1. Confirm `SwitchingBackupRestoreCertificationStale` is active in Prometheus and Alertmanager, including labels and receiver.
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

