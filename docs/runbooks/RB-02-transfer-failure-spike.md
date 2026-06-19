# RB-02 — Transfer Failure Spike

**Trigger:** Alert `transfer fail rate > 10%` in a 1-minute window, or customer escalation of payment failures.  
**Severity:** CRITICAL during business hours; HIGH off-hours.  
**On-call contacts:** payment-team, ops-lead

---

## 1. Confirm the alert

```bash
# Recent failed transfers
kubectl exec -n switching deploy/switching-api -- \
  curl -s -H "X-API-Key: $OPS_KEY" \
  "http://localhost:8080/api/operations/transfers?status=FAILED&limit=20"

# Failure counter (last reset at pod start)
kubectl exec -n switching deploy/switching-api -- \
  curl -s http://localhost:9090/actuator/metrics/payment.outbox.dispatch.failed
```

---

## 2. Classify the failure

### 2a. Business failures (EXT-001 — downstream bank rejected)

```bash
# Look at error codes in recent failures
kubectl exec -n switching deploy/switching-api -- \
  curl -s -H "X-API-Key: $OPS_KEY" \
  "http://localhost:8080/api/operations/outbox-failures" | jq '.[] | .errorCode'
```

EXT-001 = bank-side reject. Check with bank team whether they have a known issue.

### 2b. Network failures (NET-001/002/003/004)

Check connector health (see RB-01 §2a). These auto-retry.

### 2c. Validation errors (REQ-001 through REQ-009)

Check if a client is sending malformed requests:
```bash
kubectl logs -n switching deploy/switching-api --since=15m | grep "REQ-0"
```

If yes → contact the bank/PSP team sending the bad requests.

### 2d. Trace a specific failing transferRef

```bash
TRANSFER_REF="TXN-20260515-XXXXXX"
kubectl exec -n switching deploy/switching-api -- \
  curl -s -H "X-API-Key: $OPS_KEY" \
  "http://localhost:8080/api/operations/transfers/${TRANSFER_REF}/trace"
```

---

## 3. Mitigation

| Failure type | Action |
|-------------|--------|
| EXT-001 (bank reject) | Contact bank ops. Check PACS.008 content. |
| NET-* (connectivity) | See RB-03. Auto-retry is active. |
| REQ-* (validation) | Contact the PSP sending malformed data. |
| Mass failure of new deployment | Rollback — see RB-06. |

---

## 4. Recovery verification

```bash
# Monitor success/failure ratio over the next 5 minutes
kubectl exec -n switching deploy/switching-api -- \
  curl -s http://localhost:9090/actuator/prometheus | \
  grep -E "payment_outbox_dispatch_(success|failed)"
```

---

## 5. Post-incident

- [ ] Identify root cause from audit logs + trace
- [ ] Notify affected banks/PSPs with estimated resolution time
- [ ] Document in incident register
