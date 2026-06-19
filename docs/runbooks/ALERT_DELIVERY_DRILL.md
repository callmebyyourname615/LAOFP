# Alert Delivery Drill

## Objective

Prove that every declared Prometheus alert reaches the approved non-paging Alertmanager drill receiver and that each alert’s runbook URL resolves to a real heading.

## Preconditions

1. Run `python3 scripts/monitoring/verify_alert_runbooks.py` and resolve all failures.
2. Configure a highest-priority Alertmanager route matching `drill="true"` to an isolated receiver such as `switching-drill-sink`.
3. Confirm the drill receiver does not page production on-call channels.
4. Obtain an approved test window and scoped Alertmanager credentials.

## Execute

```bash
export ALERTMANAGER_URL=https://alertmanager.uat.internal
export ALERTMANAGER_BEARER_TOKEN=<scoped-secret>
export ALERT_EXPECTED_RECEIVER=switching-drill-sink
export ALERT_DRILL_CONFIRMATION=I_UNDERSTAND_THIS_SENDS_TEST_ALERTS
export ALERT_DELIVERY_OUTPUT=build/alert-delivery-results.json
scripts/monitoring/run_alert_delivery_drill.sh
```

The script discovers every unique alert from all `PrometheusRule` files, posts controlled synthetic alerts with `drill=true`, verifies the receiver returned by Alertmanager, records missing/wrong routes, and resolves the synthetic alerts.

## Acceptance criteria

- `alertCount` equals `observedCount`.
- `missing` is empty.
- `wrongReceiver` is empty.
- `passed` is true.
- Alertmanager logs and the drill sink independently confirm delivery.
- No production paging receiver was invoked.

## Failure response

A missing alert means route inhibition, grouping, authentication, or API delivery must be investigated. A wrong receiver is a production safety incident: resolve the synthetic alert, notify observability owners, correct route precedence, and rerun the complete drill.
