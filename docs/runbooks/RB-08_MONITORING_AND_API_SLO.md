# RB 08 MONITORING AND API SLO

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

## Switchingapiunavailable

### Alert: `SwitchingApiUnavailable`

- Severity: `critical`
- Hold: `2m`
- Rule source: `monitoring/prometheus/prometheus-rules.yaml`
- Summary: Switching API has no scrapeable management endpoint
- Description: No switching-api management target has been healthy for two minutes.

PromQL/expression:

```promql
absent(up{namespace="switching",service="switching-api-management"}) or max(up{namespace="switching",service="switching-api-management"}) < 1
```

Immediate response:

1. Confirm `SwitchingApiUnavailable` is active in Prometheus and Alertmanager, including labels and receiver.
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

## Switchingapihigherrorrate

### Alert: `SwitchingApiHighErrorRate`

- Severity: `critical`
- Hold: `5m`
- Rule source: `monitoring/prometheus/prometheus-rules.yaml`
- Summary: Switching API HTTP 5xx rate exceeds 1%
- Description: The 5-minute HTTP server error ratio is above the production threshold.

PromQL/expression:

```promql
(
  sum(rate(http_server_requests_seconds_count{application="switching-api",status=~"5.."}[5m]))
  /
  clamp_min(sum(rate(http_server_requests_seconds_count{application="switching-api"}[5m])), 0.001)
) > 0.01
```

Immediate response:

1. Confirm `SwitchingApiHighErrorRate` is active in Prometheus and Alertmanager, including labels and receiver.
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

## Switchingapihighp95Latency

### Alert: `SwitchingApiHighP95Latency`

- Severity: `warning`
- Hold: `10m`
- Rule source: `monitoring/prometheus/prometheus-rules.yaml`
- Summary: Switching API p95 latency exceeds one second
- Description: HTTP p95 latency has remained above one second for ten minutes.

PromQL/expression:

```promql
histogram_quantile(
  0.95,
  sum by (le) (rate(http_server_requests_seconds_bucket{application="switching-api"}[5m]))
) > 1
```

Immediate response:

1. Confirm `SwitchingApiHighP95Latency` is active in Prometheus and Alertmanager, including labels and receiver.
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

## Switchingoperationalmetricsstale

### Alert: `SwitchingOperationalMetricsStale`

- Severity: `warning`
- Hold: `3m`
- Rule source: `monitoring/prometheus/prometheus-rules.yaml`
- Summary: Business operational metrics are stale
- Description: The database-backed metrics collector has not refreshed successfully for three minutes.

PromQL/expression:

```promql
max(switching_ops_metrics_refresh_success{application="switching-api"}) < 1
or time() - max(switching_ops_metrics_last_success_epoch{application="switching-api"}) > 180
```

Immediate response:

1. Confirm `SwitchingOperationalMetricsStale` is active in Prometheus and Alertmanager, including labels and receiver.
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

