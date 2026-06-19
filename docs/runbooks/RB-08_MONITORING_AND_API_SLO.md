# RB-08 — Monitoring, API Availability and SLO

## Purpose

Use this runbook for management-endpoint availability, API 5xx ratio, p95 latency, and stale operational metrics. The management service is private; never expose port 9090 through the public ingress.

## Common first response

1. Acknowledge the alert and record UTC start time, release digest, affected routes, and alert labels.
2. Check `kubectl -n switching get deploy,pods,svc,endpoints` and the latest rollout status.
3. Query Prometheus for the exact alert expression and compare canary/stable tracks.
4. Preserve logs and graphs before restarting or rolling back.
5. Escalate to incident command for a critical alert lasting more than five minutes.

## SwitchingApiUnavailable

### Meaning

No healthy `switching-api-management` target has been scrapeable for two minutes.

### Diagnose

```bash
kubectl -n switching get pods -l app=switching-api -o wide
kubectl -n switching get endpoints switching-api-management -o yaml
kubectl -n switching describe deploy switching-api
kubectl -n switching logs deploy/switching-api --since=15m --all-containers=true
```

Check readiness separately from liveness. A readiness failure may be caused by database or sanctions freshness gates and should not be hidden by repeated restarts.

### Recover

- If caused by a failed rollout, use the digest recorded in deployment evidence and execute RB-06 rollback.
- If only the management Service is broken, repair selectors/NetworkPolicy without changing the application image.
- Confirm `up{namespace="switching",service="switching-api-management"} == 1` for at least five minutes.

## SwitchingApiHighErrorRate

### Meaning

The five-minute 5xx ratio is above one percent.

### Diagnose

Break down by URI, method, exception, pod, participant, and release track. Correlate with database saturation, Kafka, external connector, and outbox alerts. Do not log account numbers, webhook secrets, OAuth tokens, or raw ISO payloads.

### Recover

- Stop or roll back the faulty canary before scaling.
- Apply participant or connector isolation only through approved configuration controls.
- Close only after the ratio stays below threshold for two evaluation windows and no committed transaction is lost.

## SwitchingApiHighP95Latency

### Meaning

HTTP p95 latency has exceeded one second for ten minutes.

### Diagnose and recover

Check Hikari waiters, CPU throttling, GC pause, external connector latency, Kafka lag, and top SQL. Scaling is permitted only inside the governed autoscaling policy. If error budget is burning, freeze non-emergency releases.

## SwitchingOperationalMetricsStale

### Meaning

The database-backed collector failed its latest refresh or has not succeeded for 180 seconds.

### Diagnose

```bash
curl -fsS http://127.0.0.1:9090/actuator/prometheus \
  | grep -E 'switching_ops_metrics_(refresh_success|last_success_epoch)'
```

Review collector warnings, database connectivity, query duration, schema drift, and whether `OPERATIONAL_METRICS_ENABLED` was incorrectly disabled.

### Recover

Restore database access or correct the query/schema issue. Never suppress this alert by disabling the collector in production. Confirm refresh success equals 1 and timestamp age is below 90 seconds for five minutes.

## Evidence and closure

Attach alert firing/resolution timestamps, Prometheus screenshots or query export, pod and image digest, operator commands, transaction-integrity check, and incident/change references.
