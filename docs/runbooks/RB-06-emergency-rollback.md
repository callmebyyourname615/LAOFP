# RB-06 — Emergency Rollback Procedure

**When to use:** A new deployment causes elevated errors, crash-loops, or data integrity issues that cannot be quickly patched forward.  
**Severity:** CRITICAL — in-flight payments may be affected.  
**Roles required:** ADMIN (API), kubectl cluster access  
**Decision authority:** Engineering lead or on-call manager must authorise rollback.

---

## 1. Assess — is rollback the right action?

| Condition | Action |
|-----------|--------|
| Error rate spike only on new version, no DB migration run | ✅ Rollback safe |
| Error rate spike, DB migration ran but is backward-compatible | ✅ Rollback safe |
| Error rate spike, DB migration ran and is NOT backward-compatible | ⚠️ DB compensating migration required first — escalate to DBA |
| Crash-loop on startup | ✅ Rollback safe (migration failed to run) |

---

## 2. Kubernetes rollback (primary method)

```bash
# View rollout history
kubectl rollout history deployment/switching-api -n switching

# Rollback to previous revision
kubectl rollout undo deployment/switching-api -n switching

# Or rollback to a specific revision
kubectl rollout undo deployment/switching-api -n switching --to-revision=3
```

Monitor rollout:
```bash
kubectl rollout status deployment/switching-api -n switching
```

Pods are replaced using `maxUnavailable: 0, maxSurge: 1` — no traffic is dropped during rollback.

---

## 3. Verify the rollback succeeded

```bash
# Check running image version
kubectl get pods -n switching -o jsonpath='{range .items[*]}{.spec.containers[0].image}{"\n"}{end}'

# Health check
kubectl exec -n switching deploy/switching-api -- \
  curl -s http://localhost:9090/actuator/health | jq .status

# Smoke test a known-good transfer
kubectl exec -n switching deploy/switching-api -- \
  curl -s -H "X-API-Key: $OPS_KEY" \
  http://localhost:8080/api/operations/health
```

---

## 4. Handle in-flight payments that failed during the bad deployment

```bash
# Find events stuck in PROCESSING (pod crash mid-dispatch)
kubectl exec -n switching deploy/switching-api -- \
  curl -s -H "X-API-Key: $OPS_KEY" \
  http://localhost:8080/api/operations/outbox-stuck | jq .

# Recover stuck events
kubectl exec -n switching deploy/switching-api -- \
  curl -X POST -H "X-API-Key: $ADMIN_KEY" \
  http://localhost:8080/api/operations/outbox-stuck/recover-all

# Bulk retry FAILED events (after confirming rollback is stable)
kubectl exec -n switching deploy/switching-api -- \
  curl -X POST -H "X-API-Key: $ADMIN_KEY" \
  http://localhost:8080/api/operations/outbox-failures/retry-all
```

---

## 5. DB rollback (if migration must be undone)

> ⚠️ This is a last-resort, manual DBA action. Coordinate with DBA before proceeding.

```bash
# Check which migration was last applied
kubectl exec -n switching deploy/switching-api -- \
  curl -s http://localhost:9090/actuator/flyway | jq .

# Standard approach: write a compensating migration V<N+1>
# e.g., if V20 added a NOT NULL column without default:
# V21__compensate_V20_revert_column.sql:  ALTER TABLE oauth_clients DROP COLUMN IF EXISTS new_col;

# Apply the compensating migration manually or via the next deployment
```

**Never edit or delete applied Flyway migrations.** Always write a new migration to compensate.

---

## 6. Communicate

```
#incidents Slack channel:
"[P1] Switching API rolled back from version X to Y at HH:MM ICT.
 Root cause: [brief description].
 In-flight payments: [N] events recovered and re-queued.
 Status: Stable — monitoring for 30 minutes.
 Next steps: [hotfix / investigation ticket]"
```

Notify:
- [ ] Engineering lead
- [ ] Product / business stakeholder
- [ ] Affected bank operations teams (if payments were affected)

---

## 7. Post-incident

- [ ] Write blameless incident review (within 48h)
- [ ] Add test that would have caught the issue
- [ ] Update deployment checklist with lessons learned
- [ ] Track JIRA ticket for root cause fix
