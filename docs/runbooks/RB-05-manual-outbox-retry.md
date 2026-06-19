# RB-05 — Manual Outbox Retry Procedure

**When to use:** Transfers are stuck in FAILED state after the automatic retry schedule (30s/2min/10min) has been exhausted, or a FAILED event needs to be manually inspected and re-queued.  
**Severity:** MEDIUM — no new payment impact; stuck payments need recovery.  
**Roles required:** OPS (single event), ADMIN (bulk retry)

---

## 1. Identify stuck / failed events

```bash
# Events in FAILED state (terminal — no more auto-retries)
kubectl exec -n switching deploy/switching-api -- \
  curl -s -H "X-API-Key: $OPS_KEY" \
  http://localhost:8080/api/operations/outbox-failures | jq .

# Events stuck in PROCESSING > 5 min (possible pod crash mid-dispatch)
kubectl exec -n switching deploy/switching-api -- \
  curl -s -H "X-API-Key: $OPS_KEY" \
  http://localhost:8080/api/operations/outbox-stuck | jq .
```

Key fields to check per event:
- `errorCode` — classifies the failure type
- `retryCount` — how many attempts were made
- `transferRef` — use this to trace the full payment journey

---

## 2. Trace the transfer before retrying

Always understand *why* an event failed before retrying it:

```bash
TRANSFER_REF="TXN-20260515-XXXXXX"

kubectl exec -n switching deploy/switching-api -- \
  curl -s -H "X-API-Key: $OPS_KEY" \
  "http://localhost:8080/api/operations/transfers/${TRANSFER_REF}/trace"
```

Look at:
- `statusHistory` — state transitions
- `auditLogs` — what happened at each step
- `isoMessages` — the outbound PACS.008 content

---

## 3. Retry a single event (OPS role)

```bash
OUTBOX_EVENT_ID=12345

kubectl exec -n switching deploy/switching-api -- \
  curl -X POST -H "X-API-Key: $OPS_KEY" \
  "http://localhost:8080/api/outbox-events/${OUTBOX_EVENT_ID}/retry"
```

This resets the event to PENDING and re-queues it for the next dispatch cycle.

---

## 4. Mark an event as reviewed (without retrying)

Use when the event should NOT be retried (e.g., duplicate, compliance block):

```bash
kubectl exec -n switching deploy/switching-api -- \
  curl -X POST -H "X-API-Key: $OPS_KEY" \
  -H "Content-Type: application/json" \
  -d '{"reason": "Duplicate transfer confirmed — do not retry"}' \
  "http://localhost:8080/api/operations/outbox-events/${OUTBOX_EVENT_ID}/mark-reviewed"
```

This creates an `OUTBOX_EVENT_MARKED_REVIEWED` audit log entry and prevents the event from cluttering the FAILED view.

---

## 5. Bulk retry all FAILED events (ADMIN only)

> ⚠️ Only use after confirming the root cause is fixed (e.g., connector restored, DB issue resolved). Bulk retry on a still-broken connector will just re-fail all events.

```bash
kubectl exec -n switching deploy/switching-api -- \
  curl -X POST -H "X-API-Key: $ADMIN_KEY" \
  http://localhost:8080/api/operations/outbox-failures/retry-all
```

---

## 6. Recover stuck PROCESSING events

Stuck events are usually caused by a pod crash mid-dispatch. The recovery endpoint resets them to PENDING:

```bash
kubectl exec -n switching deploy/switching-api -- \
  curl -X POST -H "X-API-Key: $ADMIN_KEY" \
  http://localhost:8080/api/operations/outbox-stuck/recover-all
```

---

## 7. Verify recovery

```bash
# Check that the retried event moved to SUCCESS
kubectl exec -n switching deploy/switching-api -- \
  curl -s -H "X-API-Key: $OPS_KEY" \
  "http://localhost:8080/api/operations/transfers/${TRANSFER_REF}" | jq .status
```

---

## 8. Post-incident

- [ ] Record which events were manually retried in the incident log
- [ ] Every manual retry creates an `OUTBOX_MANUAL_RETRY_REQUESTED` audit entry (actor + timestamp automatically recorded)
- [ ] If the same event needed retry 3+ times, create a JIRA ticket for root cause
