# RB-03 — Connector Timeout / Bank Down

**Trigger:** Alert `NET-001/NET-002 count > 5 in 1 min`, or all transfers to a specific bank failing.  
**Severity:** HIGH (one bank affected) / CRITICAL (primary bank cluster down).  
**On-call contacts:** ops-lead, bank-partner-contacts

---

## 1. Identify which connector is failing

```bash
# Connector health overview
kubectl exec -n switching deploy/switching-api -- \
  curl -s -H "X-API-Key: $OPS_KEY" \
  http://localhost:8080/api/operations/connectors/health

# Logs showing NET-* errors
kubectl logs -n switching deploy/switching-api --since=5m | grep "NET-00"
```

---

## 2. Test the connector manually

```bash
CONNECTOR_NAME="HTTP_BANK_B_CONNECTOR"  # replace with actual name

kubectl exec -n switching deploy/switching-api -- \
  curl -X POST -H "X-API-Key: $ADMIN_KEY" \
  "http://localhost:8080/api/operations/connectors/${CONNECTOR_NAME}/test"
```

If test returns a network error → bank endpoint is unreachable.

---

## 3. Temporary suspension of the connector (optional)

If the bank is known to be down for an extended period, disable the connector to stop new transfers from queuing:

```bash
# Disable connector (ADMIN only)
kubectl exec -n switching deploy/switching-api -- \
  curl -X PATCH -H "X-API-Key: $ADMIN_KEY" \
  -H "Content-Type: application/json" \
  -d '{"enabled": false}' \
  "http://localhost:8080/api/connector-configs/${CONNECTOR_NAME}"
```

> ⚠️ Disabling will cause new transfers to the bank to fail immediately (no silent queuing). Only do this after coordinating with the bank.

Existing PENDING outbox events will continue to retry on backoff schedule.

---

## 4. Check outbox backlog for the affected bank

```bash
# How many events are waiting for this bank?
kubectl exec -n switching deploy/switching-api -- \
  curl -s -H "X-API-Key: $OPS_KEY" \
  "http://localhost:8080/api/operations/outbox-failures" | \
  jq '.[] | select(.connectorName == "HTTP_BANK_B_CONNECTOR") | .transferRef'
```

---

## 5. Re-enable and verify recovery

```bash
# Re-enable when bank is back
curl -X PATCH -H "X-API-Key: $ADMIN_KEY" \
  -H "Content-Type: application/json" \
  -d '{"enabled": true}' \
  "http://localhost:8080/api/connector-configs/${CONNECTOR_NAME}"

# Test the connector
curl -X POST -H "X-API-Key: $ADMIN_KEY" \
  "http://localhost:8080/api/operations/connectors/${CONNECTOR_NAME}/test"

# Retry backlog (if events are in FAILED state and not retrying)
curl -X POST -H "X-API-Key: $ADMIN_KEY" \
  "http://localhost:8080/api/operations/outbox-failures/retry-all"
```

---

## 6. Post-incident

- [ ] Record downtime duration in incident log
- [ ] Request RCA from bank partner
- [ ] Evaluate circuit-breaker implementation if repeated
