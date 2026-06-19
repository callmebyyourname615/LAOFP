# RB-01 — Outbox Backlog Growing

**Trigger:** Alert `payment.outbox.pending.count > 100` fires and holds for 5+ minutes.  
**Severity:** HIGH — payments are queuing; SLA at risk.  
**On-call contacts:** ops-lead, payment-team

---

## 1. Confirm the alert

```bash
# Check current counts directly from Prometheus / Grafana, or query the DB
kubectl exec -n switching deploy/switching-api -- \
  curl -s http://localhost:9090/actuator/prometheus | \
  grep payment_outbox

# Or via DB
kubectl exec -n switching deploy/switching-api -- \
  curl -s http://localhost:9090/actuator/metrics/payment.outbox.pending.count
```

Expected normal pending count: **< 20**.

---

## 2. Triage — why is the backlog growing?

### 2a. Connector down / bank unreachable

```bash
# Check connector health
kubectl exec -n switching deploy/switching-api -- \
  curl -s -H "X-API-Key: $OPS_KEY" http://localhost:8080/api/operations/connectors/health

# Check stuck PROCESSING events (stuck > 5 min = connector timeout)
kubectl exec -n switching deploy/switching-api -- \
  curl -s -H "X-API-Key: $OPS_KEY" http://localhost:8080/api/operations/outbox-stuck
```

If bank is unreachable → escalate to bank ops team; events will auto-retry with backoff (30s/2min/10min).

### 2b. App crash-loop / pods not running

```bash
kubectl get pods -n switching
kubectl logs -n switching deploy/switching-api --tail=100 | grep ERROR
```

If pods are crash-looping → see `RB-06-emergency-rollback.md`.

### 2c. DB connection exhaustion

```bash
kubectl exec -n switching deploy/switching-api -- \
  curl -s http://localhost:9090/actuator/metrics/hikaricp.connections.active
```

If `active ≈ maximumPoolSize` → check for long-running queries:
```sql
SHOW PROCESSLIST;
SELECT * FROM information_schema.innodb_trx ORDER BY trx_started;
```

### 2d. Outbox worker silently disabled

```bash
kubectl exec -n switching deploy/switching-api -- \
  curl -s http://localhost:9090/actuator/health | jq .
```

Check that `scheduling` is healthy and the pod didn't receive a shutdown signal that stuck it in `shuttingDown=true`.

---

## 3. Mitigation

| Root cause | Action |
|-----------|--------|
| Bank connector down | Wait for recovery (auto-retry). Set `force_reject=false` on recovered connector. |
| DB slow / locked | Kill long-running queries; alert DBA. |
| Worker pod stuck | `kubectl rollout restart deployment/switching-api -n switching` |
| Batch surge | HPA will scale up; monitor `payment.outbox.processing.count` |

### Manual bulk retry (ADMIN only, use sparingly)

```bash
curl -X POST -H "X-API-Key: $ADMIN_KEY" \
  http://localhost:8080/api/operations/outbox-failures/retry-all
```

> ⚠️ Only use after root cause is fixed to avoid flooding a recovering bank.

---

## 4. Verify recovery

```bash
# Pending count should drop below 20 within 5 minutes of root cause fix
watch -n 10 "kubectl exec -n switching deploy/switching-api -- \
  curl -s http://localhost:9090/actuator/metrics/payment.outbox.pending.count"
```

---

## 5. Post-incident

- [ ] Write incident summary in #incidents Slack channel
- [ ] Add JIRA ticket for root cause fix
- [ ] Review alert thresholds if false positive
