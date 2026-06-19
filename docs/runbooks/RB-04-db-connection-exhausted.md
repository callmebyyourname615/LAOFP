# RB-04 — DB Connection Pool Exhausted

**Trigger:** Alert `hikaricp.connections.active > 90% of maximumPoolSize` for 2+ minutes, or `HikariPool-1 - Connection is not available, request timed out` in logs.  
**Severity:** CRITICAL — all DB-dependent operations stall.  
**On-call contacts:** ops-lead, DBA

---

## 1. Confirm pool exhaustion

```bash
kubectl exec -n switching deploy/switching-api -- \
  curl -s http://localhost:9090/actuator/metrics/hikaricp.connections.active

kubectl exec -n switching deploy/switching-api -- \
  curl -s http://localhost:9090/actuator/metrics/hikaricp.connections.pending
```

If `active == maximumPoolSize` (default 10) and `pending > 0` → pool exhausted.

---

## 2. Identify the cause

### 2a. Long-running queries holding connections

```sql
-- Run on MySQL host as DBA
SHOW FULL PROCESSLIST;

-- Find queries running > 30 seconds
SELECT id, user, host, db, command, time, state, LEFT(info,200) AS query
  FROM information_schema.processlist
 WHERE command != 'Sleep' AND time > 30
 ORDER BY time DESC;
```

### 2b. Uncommitted transactions / locked rows

```sql
SELECT trx_id, trx_started, trx_state, trx_tables_locked
  FROM information_schema.innodb_trx;
```

### 2c. HPA not scaled enough (too few pods sharing the pool)

```bash
kubectl get hpa -n switching
kubectl describe hpa switching-api-hpa -n switching
```

If CPU/memory high and HPA at maxReplicas → connection pool per pod is a bottleneck.

---

## 3. Immediate mitigation

### Kill the blocking query (DBA action)

```sql
-- Get the process id from SHOW PROCESSLIST
KILL QUERY <process_id>;
-- If query doesn't die
KILL <process_id>;
```

### Restart the app pod (forces reconnect, clears stuck PROCESSING events)

```bash
kubectl rollout restart deployment/switching-api -n switching
```

> This will trigger graceful shutdown (30s drain) and new pods will reconnect.

---

## 4. Tune connection pool (if chronic)

Edit `k8s/configmap.yaml` and increase `HIKARI_MAX_POOL_SIZE`:

```yaml
HIKARI_MAX_POOL_SIZE: "20"   # was 10; check MySQL max_connections first
```

Then:
```bash
kubectl apply -f k8s/configmap.yaml
kubectl rollout restart deployment/switching-api -n switching
```

Check MySQL `max_connections`:
```sql
SHOW VARIABLES LIKE 'max_connections';
-- Total connections = HIKARI_MAX_POOL_SIZE × number_of_pods
-- Must be < max_connections × 0.8 (leave headroom for migrations)
```

---

## 5. Verify recovery

```bash
watch -n 5 "kubectl exec -n switching deploy/switching-api -- \
  curl -s http://localhost:9090/actuator/metrics/hikaricp.connections.active"
```

Active connections should drop below 70% of pool size within 2 minutes.

---

## 6. Post-incident

- [ ] Review and tune `HIKARI_MAX_POOL_SIZE` and MySQL `max_connections`
- [ ] Add DB slow-query log analysis to weekly health review
- [ ] Consider adding read replica for reporting/audit queries (P8 scope)
