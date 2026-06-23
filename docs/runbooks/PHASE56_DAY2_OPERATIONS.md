# PHASE56 DAY2 OPERATIONS

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

## Switching Storage Forecast Below90 Days

### Alert: `SwitchingStorageForecastBelow90Days`

- Severity: `warning`
- Hold: `1h`
- Rule source: `monitoring/prometheus/phase56-capacity-rules.yaml`
- Summary: Storage forecast below 90 days
- Description: Forecasted storage headroom is below 90 days and capacity expansion planning is required.

PromQL/expression:

```promql
switching_storage_forecast_days < 90
```

Immediate response:

1. Confirm `SwitchingStorageForecastBelow90Days` is active in Prometheus and Alertmanager, including labels and receiver.
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

## Switching Resilience Certificate Expired

### Alert: `SwitchingResilienceCertificateExpired`

- Severity: `critical`
- Hold: `5m`
- Rule source: `monitoring/prometheus/phase56-resilience-rules.yaml`
- Summary: Resilience certificate expired
- Description: The current resilience certification has expired and failure recovery must be re-certified.

PromQL/expression:

```promql
switching_resilience_certificate_valid_until_timestamp_seconds < time()
```

Immediate response:

1. Confirm `SwitchingResilienceCertificateExpired` is active in Prometheus and Alertmanager, including labels and receiver.
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

## Switching Fast Error Budget Burn

### Alert: `SwitchingFastErrorBudgetBurn`

- Severity: `critical`
- Hold: `2m`
- Rule source: `monitoring/prometheus/phase56-slo-rules.yaml`
- Summary: Fast SLO error-budget burn
- Description: The short-window burn rate is consuming the service error budget fast enough to require immediate release and incident controls.

PromQL/expression:

```promql
switching_slo_burn_rate_5m > 14.4 and switching_slo_burn_rate_1h > 14.4
```

Immediate response:

1. Confirm `SwitchingFastErrorBudgetBurn` is active in Prometheus and Alertmanager, including labels and receiver.
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

## Switching Slow Error Budget Burn

### Alert: `SwitchingSlowErrorBudgetBurn`

- Severity: `warning`
- Hold: `15m`
- Rule source: `monitoring/prometheus/phase56-slo-rules.yaml`
- Summary: Slow SLO error-budget burn
- Description: The multi-window burn rate indicates sustained SLO degradation that requires corrective action.

PromQL/expression:

```promql
switching_slo_burn_rate_30m > 6 and switching_slo_burn_rate_6h > 6
```

Immediate response:

1. Confirm `SwitchingSlowErrorBudgetBurn` is active in Prometheus and Alertmanager, including labels and receiver.
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

