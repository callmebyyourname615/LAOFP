# RB 10 TRANSACTION SETTLEMENT AND WEBHOOK

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

## Switchingtransactionrejectspike

### Alert: `SwitchingTransactionRejectSpike`

- Severity: `critical`
- Hold: `5m`
- Rule source: `monitoring/prometheus/prometheus-rules.yaml`
- Summary: Transaction rejects exceed the five-minute threshold
- Description: More than 20 transactions were rejected in the rolling five-minute window.

PromQL/expression:

```promql
max(switching_ops_transactions_rejected_last5m{application="switching-api"}) > 20
```

Immediate response:

1. Confirm `SwitchingTransactionRejectSpike` is active in Prometheus and Alertmanager, including labels and receiver.
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

## Switchingsettlementfailure

### Alert: `SwitchingSettlementFailure`

- Severity: `critical`
- Hold: `2m`
- Rule source: `monitoring/prometheus/prometheus-rules.yaml`
- Summary: A settlement cycle failed today
- Description: At least one settlement cycle is in FAILED state and requires operator action.

PromQL/expression:

```promql
max(switching_ops_settlement_failed_today{application="switching-api"}) > 0
```

Immediate response:

1. Confirm `SwitchingSettlementFailure` is active in Prometheus and Alertmanager, including labels and receiver.
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

## Switchingwebhookdeliveryfailure

### Alert: `SwitchingWebhookDeliveryFailure`

- Severity: `warning`
- Hold: `5m`
- Rule source: `monitoring/prometheus/prometheus-rules.yaml`
- Summary: Webhook delivery is failing or overdue
- Description: Final failures exist or more than 25 webhook deliveries are overdue.

PromQL/expression:

```promql
max(switching_ops_webhook_failed_final{application="switching-api"}) > 0
or max(switching_ops_webhook_pending_overdue{application="switching-api"}) > 25
```

Immediate response:

1. Confirm `SwitchingWebhookDeliveryFailure` is active in Prometheus and Alertmanager, including labels and receiver.
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

