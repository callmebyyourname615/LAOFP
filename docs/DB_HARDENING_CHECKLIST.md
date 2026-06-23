# Database Hardening Checklist

> **Last updated:** 2026-06-23
> **Audience:** Backend + DBA + SRE
> **Purpose:** ปรับ DB จาก 89% → 96% production-grade
> **Convention:**
> - `[x]` = done & evidence on file
> - `[~]` = in progress / partial
> - `[ ]` = not started
> - `[-]` = N/A / deferred
> - 🔴 Tier 1 = ต้องทำก่อน UAT load test
> - 🟡 Tier 2 = ก่อน Go-Live
> - 🟢 Tier 3 = Year 1 enhancement

---

## 📊 Progress Tracker

| Tier | Items | Done | Progress |
|---|---|---|---|
| 🔴 Tier 1 (Pre-UAT) | 24 | 0 | 0% |
| 🟡 Tier 2 (Pre-GoLive) | 32 | 0 | 0% |
| 🟢 Tier 3 (Year 1) | 18 | 0 | 0% |
| **Overall** | **74** | **0** | **0%** |

---

# 🔴 TIER 1 — Pre-UAT Load Test (~2 days)

## T1.1 — PgBouncer Connection Pool

**Why:** 10K TPS × 30 conn/pod × 30 pods = 900 active → Postgres `max_connections=100` will crash
**Effort:** 1 hour
**Migration ใหม่:** ไม่มี (config เท่านั้น)

### Steps

- [ ] Add `pgbouncer` service to `docker-compose.yml`
  ```yaml
  pgbouncer:
    image: edoburu/pgbouncer:latest
    environment:
      DATABASE_URL: postgresql://switching:${POSTGRES_PASSWORD}@postgres:5432/switching_db
      POOL_MODE: transaction
      MAX_CLIENT_CONN: 2000
      DEFAULT_POOL_SIZE: 100
      RESERVE_POOL_SIZE: 25
      QUERY_TIMEOUT: 60
      QUERY_WAIT_TIMEOUT: 30
      IDLE_TRANSACTION_TIMEOUT: 60
    ports:
      - "6432:6432"
    depends_on:
      postgres:
        condition: service_healthy
  ```
- [ ] Update app `DB_URL` to point to PgBouncer port 6432
- [ ] Disable Hibernate prepared statement caching (incompatible with transaction pooling)
  ```properties
  spring.jpa.properties.hibernate.jdbc.use_get_generated_keys=false
  spring.jpa.properties.hibernate.temp.use_jdbc_metadata_defaults=false
  spring.datasource.hikari.data-source-properties.prepareThreshold=0
  ```
- [ ] Add k8s manifest `k8s/pgbouncer-deployment.yaml`
- [ ] Add Prometheus metrics endpoint (port 9127 with `pgbouncer_exporter`)
- [ ] Add integration test that verifies app can connect via PgBouncer
- [ ] Update [docs/database-and-architecture-design.md] with pool architecture
- [ ] Verify: `psql -h pgbouncer -p 6432 -U switching` succeeds
- [ ] Verify: HikariCP connects, app starts, no prepared-stmt warning
- [ ] Verify under load: PgBouncer stats show pooling

### Definition of Done
- [ ] `docker compose up` brings PgBouncer healthy
- [ ] App connects through pool successfully
- [ ] k6 smoke test passes with no `too many connections` error
- [ ] Document `docs/runbooks/PGBOUNCER_OPERATIONS.md`

---

## T1.2 — Read Replica Routing

**Why:** ตอนนี้ทุก query ไป primary หมด replica idle — ลด load primary ได้ 50–70%
**Effort:** 1 day
**Migration ใหม่:** ไม่มี (Spring config + code)

### Steps

#### Config
- [ ] Add `application.yml` config (already has Postgres replica in docker-compose)
  ```yaml
  spring:
    datasource:
      primary:
        url: ${DB_URL}
        username: ${DB_APP_USERNAME:switching_app}
        password: ${DB_APP_PASSWORD}
        hikari:
          maximum-pool-size: 30
          minimum-idle: 5
          pool-name: SwitchingPrimary
      replica:
        url: ${REPLICA_DB_URL:jdbc:postgresql://postgres-read-replica:5432/switching_db}
        username: ${DB_APP_USERNAME:switching_app}
        password: ${DB_APP_PASSWORD}
        read-only: true
        hikari:
          maximum-pool-size: 20
          minimum-idle: 5
          pool-name: SwitchingReplica
  ```

#### Code

- [ ] Create `src/main/java/com/example/switching/config/ReplicaAwareRoutingDataSource.java`
  ```java
  public class ReplicaAwareRoutingDataSource extends AbstractRoutingDataSource {
      @Override
      protected Object determineCurrentLookupKey() {
          return TransactionSynchronizationManager.isCurrentTransactionReadOnly()
              ? "replica" : "primary";
      }
  }
  ```
- [ ] Create `DataSourceRoutingConfig` `@Configuration` with 2 DataSource beans + LazyConnectionDataSourceProxy
- [ ] Annotate Flyway to use `@Qualifier("primaryDataSource")` (must never run on replica)
- [ ] Mark `@Transactional(readOnly = true)` on:
  - [ ] `dashboard/settlement/service/SettlementDashboardService`
  - [ ] `dashboard/risk/service/RiskDashboardService`
  - [ ] `dashboard/crossborder/service/CrossBorderDashboardService`
  - [ ] `operations/service/OperationsDashboardService`
  - [ ] `reportdelivery/service/ReportQueryService` (if exists)
  - [ ] `audit/service/AuditQueryService`
  - [ ] All `*QueryService` classes

#### Test
- [ ] Add `src/test/java/.../config/ReadReplicaRoutingIntegrationTest.java`
  ```java
  @Test @Transactional(readOnly = true)
  void readOnlyRoutesToReplica() throws SQLException {
      try (var c = ds.getConnection();
           var rs = c.createStatement().executeQuery("SELECT pg_is_in_recovery()")) {
          rs.next();
          assertThat(rs.getBoolean(1)).isTrue();   // ✅ on replica
      }
  }
  ```
- [ ] Add observability counter: `db_connections{route="primary|replica"}`
- [ ] Document read-your-writes consistency policy in `docs/runbooks/READ_REPLICA.md`

### Definition of Done
- [ ] IT test confirms read-only TX → replica, write TX → primary
- [ ] Dashboard endpoints query replica (verified via `pg_stat_activity`)
- [ ] Metric `db_replica_route_total` increases under load
- [ ] Document: replica lag tolerance + fallback policy

---

## T1.3 — pg_stat_statements + Postgres Exporter

**Why:** Identify slow queries before they kill prod
**Effort:** 2 hours
**Migration ใหม่:** V102 to enable extension

### Steps

#### Postgres config

- [ ] Add to `docker-compose.yml` postgres command:
  ```yaml
  postgres:
    command: >
      postgres
      -c shared_preload_libraries=pg_stat_statements
      -c pg_stat_statements.track=all
      -c pg_stat_statements.max=10000
      -c track_io_timing=on
      -c log_min_duration_statement=1000
  ```

#### Migration V102

- [ ] Create `src/main/resources/db/migration/V102__enable_pg_stat_statements.sql`
  ```sql
  CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
  GRANT pg_read_all_stats TO switching_app;

  -- Helper view for slow queries top 20
  CREATE OR REPLACE VIEW v_slow_queries AS
  SELECT
      substring(query, 1, 200) AS query_excerpt,
      calls,
      round(total_exec_time::numeric, 2) AS total_ms,
      round(mean_exec_time::numeric, 2) AS mean_ms,
      round(max_exec_time::numeric, 2) AS max_ms,
      rows
  FROM pg_stat_statements
  ORDER BY total_exec_time DESC
  LIMIT 20;
  ```

#### Postgres Exporter

- [ ] Add `postgres-exporter` service to `docker-compose.yml`
  ```yaml
  postgres-exporter:
    image: prometheuscommunity/postgres-exporter:v0.15.0
    environment:
      DATA_SOURCE_NAME: "postgresql://switching:${POSTGRES_PASSWORD}@postgres:5432/switching_db?sslmode=disable"
    ports: ["9187:9187"]
  ```
- [ ] Add Prometheus scrape config for `postgres-exporter:9187`
- [ ] Add Grafana dashboard import: 9628 (PostgreSQL Database)
- [ ] Verify metrics: `pg_stat_database_xact_commit`, `pg_stat_statements_calls_total`

### Definition of Done
- [ ] `SELECT * FROM v_slow_queries` returns rows
- [ ] Prometheus has `pg_*` metrics
- [ ] Grafana shows: connections, locks, replication lag, cache hit
- [ ] Slow query alert wired (T1.4)

---

## T1.4 — Replication Lag Alert + Long-Running Query Alert

**Why:** Replication lag > 30s = data loss risk; query > 30s = blocking writes
**Effort:** 1 hour

### Steps

- [ ] Add to `monitoring/prometheus/alerts.yaml`:
  ```yaml
  - alert: PostgresReplicationLagHigh
    expr: pg_replication_lag_seconds > 30
    for: 2m
    labels: { severity: critical }
    annotations:
      summary: "Replication lag {{ $value }}s on {{ $labels.instance }}"
      runbook: docs/runbooks/REPLICATION_LAG.md

  - alert: PostgresLongRunningQuery
    expr: pg_stat_activity_max_tx_duration{state="active"} > 30
    for: 1m
    labels: { severity: warning }

  - alert: PostgresConnectionsHigh
    expr: pg_stat_database_numbackends / pg_settings_max_connections > 0.8
    for: 5m
    labels: { severity: warning }

  - alert: PostgresDeadlockRate
    expr: rate(pg_stat_database_deadlocks[5m]) > 0
    for: 1m
    labels: { severity: warning }
  ```
- [ ] Create `docs/runbooks/REPLICATION_LAG.md`
- [ ] Test alert by stopping replica for 3 min → confirm fires
- [ ] Test alert by running `SELECT pg_sleep(60)` → confirm fires

### Definition of Done
- [ ] 4 alerts firing rules in Prometheus
- [ ] Alertmanager route to right channel
- [ ] Both runbooks committed
- [ ] Confirmed firing via synthetic test

---

# 🟡 TIER 2 — Pre-GoLive (~5 days)

## T2.1 — Missing Critical Indexes

**Why:** Query plans currently do full scan on hot tables
**Effort:** 0.5 day
**Migration ใหม่:** V103

### Steps

- [ ] Run `EXPLAIN ANALYZE` on top 20 slow queries from `v_slow_queries`
- [ ] Document missing index hypothesis in `docs/db/MISSING_INDEXES.md`
- [ ] Create migration `V103__add_critical_performance_indexes.sql`:
  ```sql
  -- Transaction lookups (most common pattern)
  CREATE INDEX CONCURRENTLY idx_transactions_participant_date
      ON transactions (sender_participant_id, business_date DESC)
      INCLUDE (amount, status);

  CREATE INDEX CONCURRENTLY idx_transactions_receiver_date
      ON transactions (receiver_participant_id, business_date DESC)
      INCLUDE (amount, status);

  -- Outbox dispatch hot path
  CREATE INDEX CONCURRENTLY idx_outbox_pending_dispatch
      ON outbox_events (status, scheduled_at ASC)
      WHERE status IN ('PENDING','RETRYING');

  -- Idempotency lookup
  CREATE INDEX CONCURRENTLY idx_idempotency_key_active
      ON idempotency_records (idempotency_key)
      WHERE expires_at > now();

  -- Dispute SLA monitoring
  CREATE INDEX CONCURRENTLY idx_dispute_pending_sla
      ON dispute_cases (status, created_at DESC)
      WHERE status IN ('OPEN','IN_INVESTIGATION');

  -- Settlement netting
  CREATE INDEX CONCURRENTLY idx_settlement_net_cycle
      ON settlement_net_positions (cycle_id, participant_id);
  ```
- [ ] Use `CREATE INDEX CONCURRENTLY` so it doesn't lock the table
- [ ] Update Flyway to allow non-transactional migration:
  ```sql
  -- Flyway config: V103 must run outside transaction
  -- Add file V103__add_critical_performance_indexes.conf
  -- transactional=false
  ```
- [ ] Validate: `EXPLAIN ANALYZE` shows Index Scan instead of Seq Scan
- [ ] Document each index purpose in `docs/db/INDEX_CATALOG.md`

### Definition of Done
- [ ] V103 applied successfully on UAT
- [ ] Top 20 queries from `v_slow_queries` show < 100ms p95
- [ ] No duplicate indexes (`SELECT * FROM pg_stat_user_indexes WHERE idx_scan = 0`)
- [ ] Index catalog documented

---

## T2.2 — WAL Archiving to S3 (Real PITR)

**Why:** Without continuous WAL archive, PITR is impossible
**Effort:** 0.5 day

### Steps

- [ ] Add `pgBackRest` or `wal-g` sidecar to Postgres
  ```yaml
  postgres:
    environment:
      WALG_S3_PREFIX: s3://switching-wal/
      AWS_ACCESS_KEY_ID: ${WAL_S3_KEY}
      AWS_SECRET_ACCESS_KEY: ${WAL_S3_SECRET}
    command: >
      postgres
      -c archive_mode=on
      -c archive_command='wal-g wal-push %p'
      -c archive_timeout=60
      -c max_wal_senders=10
  ```
- [ ] Create S3 bucket `switching-wal` with Object Lock (WORM, 10-year retention per BRD §6.5)
- [ ] Create IAM role for WAL archiving (write-only to bucket)
- [ ] Run base backup nightly: `wal-g backup-push /var/lib/postgresql/data`
- [ ] Test restore to specific PITR timestamp on UAT DR site
- [ ] Document `docs/runbooks/PITR_RESTORE.md`
- [ ] Add Prometheus alert: WAL archive failure
  ```yaml
  - alert: PostgresWalArchiveFailed
    expr: pg_stat_archiver_failed_count > pg_stat_archiver_archived_count * 0.01
    labels: { severity: critical }
  ```

### Definition of Done
- [ ] `pg_stat_archiver` shows `failed_count = 0`
- [ ] S3 bucket receives WAL files every ≤ 60s
- [ ] PITR drill successful: restore to specific timestamp matches expected state
- [ ] RPO measured ≤ 1 minute (BRD §12.2)

---

## T2.3 — Row-Level Security (RLS)

**Why:** Multi-participant data leak prevention (defense-in-depth)
**Effort:** 1 day
**Migration ใหม่:** V104

### Steps

- [ ] Audit which tables need RLS:
  - [ ] `transactions` (participant data)
  - [ ] `disputes` (participant data)
  - [ ] `webhook_registrations` (participant secrets)
  - [ ] `participants` (own profile only)
  - [ ] `audit_logs` (AUDITOR only)
- [ ] Create V104 migration:
  ```sql
  -- Set session variable from app
  -- App side: SET LOCAL app.user_role = 'PARTICIPANT_ADMIN';
  --           SET LOCAL app.participant_id = 'BCEL';

  ALTER TABLE transactions ENABLE ROW LEVEL SECURITY;
  ALTER TABLE transactions FORCE ROW LEVEL SECURITY;

  CREATE POLICY transactions_participant_isolation ON transactions
      FOR SELECT
      USING (
          sender_participant_id = current_setting('app.participant_id', true)
          OR receiver_participant_id = current_setting('app.participant_id', true)
          OR current_setting('app.user_role', true) IN ('SYSTEM_ADMIN','OPS_ADMIN','AUDITOR')
      );

  CREATE POLICY transactions_admin_write ON transactions
      FOR ALL
      USING (current_setting('app.user_role', true) IN ('SYSTEM_ADMIN','OPS_ADMIN'));

  -- Repeat for disputes, webhook_registrations, etc.
  ```
- [ ] Add Spring AOP interceptor to set session vars per request:
  ```java
  @Component
  @RequiredArgsConstructor
  public class PostgresSessionContextFilter extends OncePerRequestFilter {
      private final JdbcTemplate jdbc;
      // After JWT auth, before service call:
      jdbc.execute("SET LOCAL app.user_role = '" + ctx.role() + "'");
      jdbc.execute("SET LOCAL app.participant_id = '" + ctx.participant() + "'");
  }
  ```
- [ ] Add IT test: PARTICIPANT_ADMIN of BCEL cannot see LDB transactions
- [ ] Document RLS policy matrix in `docs/db/RLS_POLICIES.md`

### Definition of Done
- [ ] V104 applied
- [ ] RLS enforced on 5 sensitive tables
- [ ] IT test confirms cross-participant isolation
- [ ] SYSTEM_ADMIN bypass works
- [ ] Policy matrix documented + signed off

---

## T2.4 — NUMERIC Money Precision Standardize

**Why:** Mixed precisions cause FX rounding bugs
**Effort:** 0.5 day
**Migration ใหม่:** V105

### Steps

- [ ] Decision: standardize on **`NUMERIC(24, 4)`** for all money
- [ ] Inventory current precisions:
  ```sql
  SELECT table_name, column_name, numeric_precision, numeric_scale
  FROM information_schema.columns
  WHERE data_type = 'numeric' AND numeric_scale > 0;
  ```
- [ ] Document affected columns in `docs/db/NUMERIC_STANDARDIZE.md`
- [ ] Create V105 migration with `ALTER TABLE ... ALTER COLUMN ... TYPE NUMERIC(24,4) USING ...::NUMERIC(24,4)`:
  - [ ] Convert `NUMERIC(19,4)` → `NUMERIC(24,4)` (15 columns)
  - [ ] Convert `NUMERIC(18,2)` → `NUMERIC(24,4)` (18 columns)
  - [ ] Convert `NUMERIC(20,2)` → `NUMERIC(24,4)` (4 columns)
- [ ] Update JPA entities: `@Column(precision = 24, scale = 4)`
- [ ] Add CHECK constraint: `CHECK (amount >= 0)` where missing
- [ ] Document precision policy: "All amounts/fees/balances: NUMERIC(24,4) LAK or convertible"
- [ ] Document FX rate precision separately: `NUMERIC(20, 10)`

### Definition of Done
- [ ] All money columns NUMERIC(24,4)
- [ ] JPA `validate` passes
- [ ] No rounding errors in settlement IT test
- [ ] Policy doc committed

---

## T2.5 — Autovacuum Tuning per Hot Partition

**Why:** Default autovacuum too slow → table bloat under 10K TPS
**Effort:** 0.5 day
**Migration ใหม่:** V106

### Steps

- [ ] Identify hot tables (high write rate):
  - `transactions`, `transaction_events`, `transaction_status_history`
  - `outbox_events`, `outbox_attempts`
  - `idempotency_records`
  - `iso_messages`, `iso_message_payloads`
  - `audit_logs`
- [ ] Create V106 migration:
  ```sql
  ALTER TABLE transactions SET (
      autovacuum_vacuum_scale_factor = 0.05,
      autovacuum_analyze_scale_factor = 0.02,
      autovacuum_vacuum_cost_limit = 2000,
      autovacuum_vacuum_cost_delay = 10
  );
  -- Repeat for other hot tables
  ```
- [ ] Apply same settings to future partitions via `precreate` function
- [ ] Add Prometheus alert: bloat > 30%
- [ ] Add `pgstattuple` extension for accurate bloat measurement (optional)
- [ ] Document tuning rationale in `docs/db/AUTOVACUUM_TUNING.md`

### Definition of Done
- [ ] V106 applied
- [ ] `pg_stat_user_tables.n_dead_tup` ratio stays < 10% during k6 soak 8h
- [ ] Vacuum metrics visible in Grafana

---

## T2.6 — JSON GIN Indexes

**Why:** Queries on `payload_json` do full table scan
**Effort:** 0.5 day
**Migration ใหม่:** V107

### Steps

- [ ] Identify JSON-queried tables:
  - `outbox_events.payload_json`
  - `audit_logs.payload_json`
  - `dispute_cases.evidence_json` (if exists)
- [ ] Create V107 migration:
  ```sql
  CREATE INDEX CONCURRENTLY idx_outbox_payload_gin
      ON outbox_events USING gin (payload_json jsonb_path_ops);

  CREATE INDEX CONCURRENTLY idx_audit_payload_gin
      ON audit_logs USING gin (payload_json jsonb_path_ops);
  ```
- [ ] Verify with `EXPLAIN ANALYZE SELECT ... WHERE payload_json @> '{...}'`

### Definition of Done
- [ ] GIN indexes present
- [ ] JSON containment queries < 50ms

---

## T2.7 — Backup Encryption Enforcement

**Why:** BRD §11.5 "Encryption at rest" includes backups
**Effort:** 0.5 day

### Steps

- [ ] Enable `age` encryption in `backup/bin/full-backup.sh`
  ```bash
  pg_basebackup ... | age -r $AGE_PUBLIC_KEY > backup.tar.age
  ```
- [ ] Store recipient public key in Vault `secret/switching/backup/age-pubkey`
- [ ] Store decryption private key in **separate** Vault path (defense-in-depth)
- [ ] Add IT test: backup created, can decrypt with separate key
- [ ] Update restore script to decrypt before restore
- [ ] Document key rotation procedure (every 1 year)

### Definition of Done
- [ ] Backup files end in `.tar.age` (encrypted)
- [ ] Cannot decrypt without private key
- [ ] Restore drill passes with encrypted backup
- [ ] Key rotation runbook committed

---

## T2.8 — Database User Roles + Least Privilege Audit

**Why:** All app traffic uses `switching_app` — but currently has too many grants
**Effort:** 0.5 day

### Steps

- [ ] Audit current grants:
  ```sql
  SELECT grantee, table_schema, table_name, privilege_type
  FROM information_schema.role_table_grants
  WHERE grantee IN ('switching_app','switching_flyway','switching_replicator');
  ```
- [ ] Document current state in `docs/db/ROLE_GRANTS.md`
- [ ] Tighten: `switching_app` should NOT have CREATE / DROP / ALTER on schema
- [ ] Add read-only role for dashboard:
  ```sql
  CREATE ROLE switching_readonly LOGIN PASSWORD '...';
  GRANT CONNECT ON DATABASE switching_db TO switching_readonly;
  GRANT USAGE ON SCHEMA public TO switching_readonly;
  GRANT SELECT ON ALL TABLES IN SCHEMA public TO switching_readonly;
  ```
- [ ] Update replica DataSource to use `switching_readonly`
- [ ] Add IT test: `switching_app` cannot CREATE TABLE

### Definition of Done
- [ ] Grants audit + sign-off
- [ ] Read-only role separate from write role
- [ ] Replica routes use read-only role
- [ ] Privilege escalation test fails (good)

---

# 🟢 TIER 3 — Year 1 Enhancement

## T3.1 — Column-Level Encryption for PII

**Why:** BRD §6.4 Restricted data class (national ID, phone)
**Effort:** 2 days
**Migration ใหม่:** V108

### Steps

- [ ] Identify PII columns:
  - [ ] `participants.contact_email`, `contact_phone`
  - [ ] `dispute_cases.customer_national_id` (if any)
  - [ ] `audit_logs.actor_ip_address`
- [ ] Use `pgcrypto` + Vault transit:
  ```sql
  ALTER TABLE participants
      ADD COLUMN contact_email_encrypted BYTEA,
      ADD COLUMN contact_phone_encrypted BYTEA;
  ```
- [ ] App-side encryption via `VaultTransitKeyEncryptionService`
- [ ] Backfill existing data
- [ ] Drop plaintext columns (in next release after verification)
- [ ] Document key rotation

### Definition of Done
- [ ] PII columns encrypted at rest
- [ ] Decryption requires Vault token
- [ ] Audit shows access logged

---

## T3.2 — DB-Level Audit Triggers (Defense-in-Depth)

**Why:** Currently audit through app — bypass possible via direct DB access
**Effort:** 1 day
**Migration ใหม่:** V109

### Steps

- [ ] Create generic audit trigger function
- [ ] Attach to sensitive tables: `participants`, `users`, `bilateral_agreements`, `fee_schedules`
- [ ] Write to immutable `db_audit_log` table (hash-chained)
- [ ] Add reconciliation job comparing app audit vs DB audit
- [ ] Document in `docs/runbooks/AUDIT_RECONCILIATION.md`

### Definition of Done
- [ ] Triggers fire on UPDATE/DELETE
- [ ] DB audit immutable (WORM via Postgres + revoke DELETE grant)
- [ ] Reconciliation passes daily

---

## T3.3 — Long-Running Query Auto-Cancel

**Why:** Runaway query blocks settlement
**Effort:** 0.5 day

### Steps

- [ ] Set `statement_timeout = 30s` for `switching_app`
- [ ] Set `statement_timeout = 600s` for `switching_flyway` (migrations need longer)
- [ ] Set `statement_timeout = 5min` for read replica (reporting)
- [ ] Set `idle_in_transaction_session_timeout = 60s`
- [ ] Test: query that sleeps 60s gets killed

### Definition of Done
- [ ] Timeouts enforced
- [ ] IT test confirms timeout fires
- [ ] Alert on cancel rate spike

---

## T3.4 — Table Bloat Dashboard

**Why:** Catch bloat before it causes downtime
**Effort:** 0.5 day

### Steps

- [ ] Install `pgstattuple` extension
- [ ] Create view `v_table_bloat`
- [ ] Add Grafana dashboard panel
- [ ] Add alert: bloat > 30%
- [ ] Document threshold + remediation

### Definition of Done
- [ ] Bloat view accessible
- [ ] Grafana panel live
- [ ] Alert configured

---

## T3.5 — Hourly Partition Strategy (Trigger-Based)

**Why:** ที่ peak > 5K TPS daily partition กลายเป็น 432M rows/day
**Effort:** 3 days (when triggered)
**Trigger:** Phase 54D perf test shows sustained > 5K TPS

### Steps (เมื่อ trigger)

- [ ] Build `precreate_hourly_partitions()` (V110)
- [ ] Build `consolidate_hourly_to_daily()` (V111)
- [ ] Add `@Scheduled` Spring task
- [ ] Update Flyway baseline policy
- [ ] Document migration plan

### Definition of Done
- [ ] Hot tables partitioned hourly for last 7 days
- [ ] Auto-consolidation past 7 days
- [ ] Query plan still uses partition pruning

---

## T3.6 — Schema Diff CI Gate

**Why:** Catch accidental schema drift in PR
**Effort:** 1 day

### Steps

- [ ] Add `migra` or `pg_diff` to CI
- [ ] On every PR: dump schema before + after migrations
- [ ] Post diff as PR comment
- [ ] Fail if migration not properly applied

### Definition of Done
- [ ] CI workflow `.github/workflows/db-schema-diff.yml`
- [ ] Diff posted on PR
- [ ] Sample PR confirms detection

---

## T3.7 — Logical Replication for Cross-Region DR (Phase D)

**Why:** When Phase D scale, sync replication ข้าม region too slow
**Effort:** 5 days (when needed)

### Steps

- [ ] Set up `pg_logical` slot
- [ ] Use Debezium or pg2kafka to stream
- [ ] Target DR region Postgres reapplies
- [ ] Document failback procedure

### Definition of Done
- [ ] Logical replication healthy
- [ ] RPO measured ≤ 30s
- [ ] Failover drill passes

---

# 📅 Recommended Order of Operations

```
Day 1   : T1.1 PgBouncer + T1.4 alerts
Day 2   : T1.2 Read replica routing + T1.3 pg_stat_statements
Day 3-5 : Run UAT load test → identify bottlenecks
Day 6   : T2.1 missing indexes (based on real EXPLAIN)
Day 7   : T2.2 WAL archiving
Day 8   : T2.3 RLS policies
Day 9   : T2.4 NUMERIC standardize + T2.5 autovacuum
Day 10  : T2.6 GIN indexes + T2.7 backup encryption + T2.8 grants audit
Day 11+ : Tier 3 enhancements as time permits before Year 1 anniversary
```

---

# 🎯 Success Metrics

| Metric | Before | After Tier 1 | After Tier 2 |
|---|---|---|---|
| Primary CPU under 10K TPS | 90% | 50% | 40% |
| Connection pool utilization | crash | 50% | 50% |
| Replica usage | 0% | 60% | 70% |
| Top query P95 | 800ms | 200ms | 100ms |
| Bloat ratio (after 8h soak) | unknown | 5% | < 5% |
| Replication lag | unmonitored | < 30s alerted | < 5s |
| PITR drill RPO | n/a | n/a | < 1 min |
| Multi-tenant data leak | possible | possible | blocked by RLS |
| **DB Score** | 89% | **92%** | **96%** |

---

# 📝 Notes Section (update เมื่อทำ)

## Notes from T1.1 PgBouncer
> _(record here when implementing)_

## Notes from T1.2 Read Replica Routing
> _(record here when implementing)_

## Notes from T1.3 pg_stat_statements
> _(record here when implementing)_

---

# 🔗 References

- [docs/database-and-architecture-design.md] — current schema reference (deleted, may need recreation)
- [docs/GO_LIVE_CRITICAL_PATH.md] — overall go-live tracker
- BRD §6 (Data Architecture), §10 (NFR), §11.5 (Encryption), §12 (DR)

---

*Single source of truth for DB hardening. Update inline as items complete.*
