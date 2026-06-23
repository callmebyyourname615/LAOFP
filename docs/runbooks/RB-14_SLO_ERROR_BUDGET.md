# RB 14 SLO ERROR BUDGET

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

## Fast Burn

### Alert: `SwitchingSloFastBurn`

- Severity: `critical`
- Hold: `2m`
- Rule source: `monitoring/prometheus/slo-alerts.yaml`
- Summary: Availability SLO is burning 14.4x faster than budget
- Description: Both the 5-minute and 1-hour windows exceed the fast-burn threshold for the 99.95% SLO.

PromQL/expression:

```promql
switching:sli_error_ratio:rate5m > (14.4 * 0.0005) and switching:sli_error_ratio:rate1h > (14.4 * 0.0005)
```

Immediate response:

1. Confirm `SwitchingSloFastBurn` is active in Prometheus and Alertmanager, including labels and receiver.
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

## Slow Burn

### Alert: `SwitchingSloSlowBurn`

- Severity: `warning`
- Hold: `15m`
- Rule source: `monitoring/prometheus/slo-alerts.yaml`
- Summary: Availability SLO is burning 6x faster than budget
- Description: Both the 30-minute and 6-hour windows exceed the slow-burn threshold.

PromQL/expression:

```promql
switching:sli_error_ratio:rate30m > (6 * 0.0005) and switching:sli_error_ratio:rate6h > (6 * 0.0005)
```

Immediate response:

1. Confirm `SwitchingSloSlowBurn` is active in Prometheus and Alertmanager, including labels and receiver.
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

## Error Budget Policy

### Alert: `SwitchingErrorBudgetNearlyExhausted`

- Severity: `warning`
- Hold: `30m`
- Rule source: `monitoring/prometheus/slo-alerts.yaml`
- Summary: Less than 20% of the 30-day availability budget remains
- Description: Freeze non-remediation releases until the budget recovers or an approved exception exists.

PromQL/expression:

```promql
switching:slo_error_budget_remaining:ratio30d < 0.20
```

### Alert: `SwitchingErrorBudgetExhausted`

- Severity: `critical`
- Hold: `5m`
- Rule source: `monitoring/prometheus/slo-alerts.yaml`
- Summary: The 30-day availability error budget is exhausted
- Description: Only reliability, security, or regulator-approved emergency changes may proceed.

PromQL/expression:

```promql
switching:slo_error_budget_remaining:ratio30d <= 0
```

Immediate response:

1. Confirm `SwitchingErrorBudgetNearlyExhausted`, `SwitchingErrorBudgetExhausted` is active in Prometheus and Alertmanager, including labels and receiver.
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

