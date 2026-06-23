# Go-Live Critical Path — Implementation Guide

> **Last updated:** 2026-06-23 (Phase 62 repository implementation)
> **Audience:** Engineering team implementing the remaining work
> **Purpose:** จัดลำดับสิ่งที่ต้องทำเพื่อ Go-Live แบบ priority-sorted พร้อม guide ลึกพอที่จะ implement ได้เอง
> **ขอบเขต:** เฉพาะสิ่งที่ block Go-Live — งานที่เลื่อนได้แยกไว้ใน section ท้าย

---

## Implementation status — repository update 2026-06-23

| Critical path | Repository status | Runtime/operator status |
|---|---|---|
| P0.1 Test remediation | 🟡 Four historical blocker fixes have static regression guards; V106/99 migration assertions prepared | `./mvnw clean verify` remains mandatory on a runner with Maven/Testcontainers |
| P0.2 Secret exposure | 🟡 Safe templates, production contract, generator and rotation checklist implemented | 🔴 SecOps must rotate live credentials, purge history and invalidate clones/caches |
| P0.3 Performance | 🟡 10K sustained, 20K burst and 5K/8h soak scenarios implemented | 🔴 UAT load execution and signed evidence pending |
| P0.4 Backup/DR | 🟡 DR orchestration and evidence contracts implemented | 🔴 Backup/PITR/failure drills pending on UAT |
| P0.5 SMOS access | 🟡 V97/V101, permission matrix, bounded API pagination and OpenAPI contract implemented | Integration suite and initial operator provisioning pending |
| P1.6 Dashboards | 🟡 Settlement, Risk and Cross-Border APIs hardened with RBAC, no-store, freshness and replica reads | UAT data/latency validation pending |
| P1.7 Promotion | 🟡 Safe JSON DSL plus V105 budget reservation/funder ledger/expiry controls implemented; disabled by default | Product decision and UAT financial reconciliation pending |
| Phase 62D–J | 🟡 Read routing, precision, Hikari monitoring, N+1 diagnostics and OTLP tracing implemented | Strict gate blocked until authoritative V91–V96 are present; runtime evidence pending |

> Status `🟡` means code/config is present but is **not evidence of runtime certification**.

---

## 🚦 Priority Matrix

| Tier | งาน | บัง bock อะไร | Effort | จำเป็นจริง? |
|---|---|---|---|---|
| **P0** | 1. Fix `mvn verify` 12 errors | ระบบ start ไม่ได้ | 3 ชม. | ⚡ **MANDATORY** |
| **P0** | 2. Rotate 6 exposed secrets + git history purge | Security breach | 6 ชม. | ⚡ **MANDATORY** |
| **P0** | 3. Performance proof (10K TPS sustained, 20K burst) | BRD ACC-026 | 12–15 วัน | ⚡ **MANDATORY** |
| **P0** | 4. Backup/PITR + DR drill evidence | RPO/RTO sign-off | 5 วัน | ⚡ **MANDATORY** |
| **P0** | 5. SMOS User & Access Management | Operator ใช้ไม่ได้ | 20 วัน | ⚡ **MANDATORY** |
| **P1** | 6. Dashboard suite (3 critical: Settlement, Risk, Cross-Border) | Operator มองไม่เห็น | 15 วัน | 🟡 ผ่าน UAT ได้ แต่ Ops จะลำบาก |
| **P1** | 7. Promotion eligibility DSL (ปิดที่ Phase II เปิดทิ้งไว้) | feature ทำงานไม่ครบ | 15 วัน | 🟡 ถ้าไม่ใช้ promotion = feature flag off |
| **P2** | 8. WhatsApp notification channel | Multi-channel alert | 10 วัน | 🟢 ใช้ Email + Dashboard ไปก่อนได้ |
| **P2** | 9. One-click DR switchover UI | Manual playbook ใช้ได้ | 12 วัน | 🟢 SRE ใช้ runbook แทน |

**สรุป:** P0 ทั้งหมดต้องทำ — ประมาณ **~45 วัน** (กับ 2–3 คน parallel = **3 สัปดาห์ calendar**)

---

# P0 — MANDATORY (ต้องทำก่อน Go-Live)

## P0.1 — Fix `mvn verify` 12 errors

### Why critical
`mvn verify` คือ gate มาตรฐานที่ CI/UAT/Prod ใช้ ถ้าไม่ผ่าน → ไม่มี RC tag → ไม่มี go-live

### What's failing (จาก surefire reports)

| # | ปัญหา | Test ที่ fail | Root cause |
|---|---|---|---|
| a | Flyway version assertion ค้างที่ "84" | `V83CleanInstallCertificationIntegrationTest`<br>`MigrationApplicationIntegrationTest`<br>`V83PayloadSha256SchemaAlignmentIntegrationTest` | ตอนนี้ migration ล่าสุดคือ V100 ไม่ใช่ V84 |
| b | `vaultTransitKeyEncryptionService` ต้องการ ObjectMapper bean | `MigrationApplicationIntegrationTest` | บาง test profile load vault-transit แต่ JacksonAutoConfig ไม่ wire |
| c | `sanctions_lists.provider_uid` NULL constraint violation | `SanctionsScreeningIntegrationTest` | test seed data ไม่ใส่ provider_uid |
| d | FK violation `psp_suspension_log_psp_id_fkey` ใน cleanup | `OperationsGenerateRoutesForBankIntegrationTest` | test ลบ participants ก่อน clear suspension log |
| e | `Can't infer SQL type for java.time.Instant` | `CrossBorderAmlBlockIntegrationTest` | ใช้ `setObject(idx, instant)` แทน `setObject(idx, instant, Types.TIMESTAMP_WITH_TIMEZONE)` |

### Step-by-step

```bash
# Step 1 — Update version assertions (3 ไฟล์, 5 จุด)
# In each test file, change isEqualTo("84") → isEqualTo("100")
#                       change isEqualTo(84L) → isEqualTo(95L)   (V1–V100 โดยเว้น V88–V90 และ V98–V99)

# Step 2 — Vault ObjectMapper issue
# In src/test/resources/application-test.yml, confirm:
#   switching.webhook.encryption.provider: local
# Then grep src/test for @TestPropertySource that may override to vault-transit:
grep -rln "vault-transit" src/test/java
# Fix any test that forces vault-transit by adding ObjectMapper bean or switching to local

# Step 3 — Sanctions test seed
# In src/test/java/.../aml/SanctionsScreeningIntegrationTest.java
# Add provider_uid column to INSERT statements or use ON CONFLICT DO NOTHING

# Step 4 — FK cleanup order  
# In src/test/java/.../operations/OperationsGenerateRoutesForBankIntegrationTest.java
# Reorder @AfterEach cleanup: psp_suspension_log → participants

# Step 5 — Instant SQL type
# In src/main/java/.../crossborder/... search for setObject usages with Instant
# Change setObject(idx, instant) to setObject(idx, instant, Types.TIMESTAMP_WITH_TIMEZONE)
```

### Verification
```bash
./mvnw -q verify 2>&1 | grep "Tests run:" | tail -1
# Expected: Failures: 0, Errors: 0 (total count increases as new SMOS/dashboard tests are added)
```

### Definition of Done
- `./mvnw verify` exit 0
- Static verifier `verify_phase53b_schema_alignment.py` green
- `./scripts/execute-and-verify/00-run-all.sh` step 03 ผ่าน

---

## P0.2 — Rotate exposed secrets + git history purge

### Why critical
ใน git history มี credentials default (placeholder credentials and previously committed development defaults) — anyone with clone access เห็น เป็น GDPR/PCI breach ได้

### 6 credentials ที่ต้อง rotate

| Credential | ปัจจุบัน (dev default) | Update ที่ไหน |
|---|---|---|
| `POSTGRES_PASSWORD` | `[REDACTED_EXPOSED_VALUE]` | Vault prod + dev `.env` |
| `REPLICATION_PASSWORD` | `[REDACTED_EXPOSED_VALUE]` | Vault prod + dev `.env` |
| `DB_APP_PASSWORD` | `[REDACTED_EXPOSED_VALUE]` | Vault prod + dev `.env` |
| `FLYWAY_PASSWORD` | `[REDACTED_EXPOSED_VALUE]` | Vault prod + dev `.env` |
| `ARCHIVE_POSTGRES_PASSWORD` | `[REDACTED_EXPOSED_VALUE]` | Vault prod + dev `.env` |
| `MINIO_ROOT_PASSWORD` | `[REDACTED_EXPOSED_VALUE]` | Vault prod + dev `.env` |

### Step-by-step

```bash
# Step 1 — Generate new strong passwords (32 chars, base64)
for cred in POSTGRES REPLICATION DB_APP FLYWAY ARCHIVE_POSTGRES MINIO_ROOT; do
  echo "${cred}=$(openssl rand -base64 32 | tr -d '/+=' | head -c 32)"
done > /tmp/new-prod-secrets.env

# Step 2 — เก็บเข้า Vault (โดย SecOps)
# vault kv put secret/switching/prod @/tmp/new-prod-secrets.env

# Step 3 — Purge contaminated paths in an isolated mirror clone
# Coordinate a repository write freeze and complete credential rotation first.
git clone --mirror <REPOSITORY_URL> switching-purge.git
security/scripts/purge-sensitive-history.sh \
  --repo switching-purge.git \
  --output-dir build/security-history/purge \
  --execute --acknowledge-credential-rotation
# Review full-history scans and evidence before the repository coordinator force-pushes.
# Follow docs/security/GIT_HISTORY_PURGE_RUNBOOK.md; the script never force-pushes automatically.

# Step 4 — Invalidate clones + CI caches
# - Notify everyone to re-clone
# - Clear GitHub Actions cache (Settings → Actions → Caches → Delete all)
# - Rotate any service account that uses the repo

# Step 5 — ลบ /tmp/new-prod-secrets.env
shred -u /tmp/new-prod-secrets.env
```

### Verification
```bash
# Confirm new passwords work
docker compose down && docker compose up -d
docker compose exec postgres psql -U switching -d switching_db -c "SELECT 1;"
# Should succeed with new password from .env

# Confirm git history is clean
git log --all --grep "change_me"  # Should return nothing
```

### Definition of Done
- ทุก credential ใน Vault prod
- Git history ไม่มี `change_me` หรือ placeholder values
- Sign `SECRET_ROTATION_CHECKLIST.md` (ผู้ดูแล repo + SecOps)
- Team รับทราบ re-clone + cache invalidation

---

## P0.3 — Performance Proof: 10K TPS sustained + 20K burst

### Why critical
BRD ACC-026 บังคับว่า "Performance testing results documented and approved" ก่อน Go-Live
ถ้าไม่มี evidence → ไม่ผ่าน regulator sign-off → ไม่ go-live

### Test scenarios ที่ต้องรัน

| Scenario | Target | Duration | Pass criteria |
|---|---|---|---|
| **Smoke** | 100 TPS | 5 นาที | error rate = 0%, P95 < 200ms |
| **Sustained 2K** | 2,000 TPS | 30 นาที | error rate < 0.1%, P95 < 300ms |
| **Sustained 10K** ⚡ | 10,000 TPS | 60 นาที | error rate < 0.1%, P95 < 500ms (BRD requirement) |
| **Burst 20K** ⚡ | 20,000 TPS | 15 นาที | error rate < 0.5%, no DB connection exhaustion (BRD requirement) |
| **Soak 8h** | 5,000 TPS | 8 ชม. | no memory leak, GC pause < 100ms, no Kafka lag growth |
| **Settlement 500k** | 500,000 tx/batch | n/a | settlement cycle complete < SLA |

### Step-by-step

```bash
# Step 1 — Setup UAT environment (ใกล้เคียง prod)
# Minimum: 4 app pods × (4 vCPU, 8 GB), Postgres r6g.4xlarge, Kafka 3-broker
# Variables:
export UAT_URL=https://uat.switching.example.com
export RUN_ID=$(date +%Y%m%d-%H%M%S)
mkdir -p performance/results/$RUN_ID

# Step 2 — Run scenarios ตามลำดับ (smoke ก่อนเสมอ)
./performance/scripts/run-k6.sh smoke      | tee performance/results/$RUN_ID/smoke.log
./performance/scripts/run-k6.sh sustained2k| tee performance/results/$RUN_ID/sustained2k.log

# ⚠️ ก่อน 10K — ตรวจ infra metrics: CPU/RAM/connection pool ห้ามเต็ม
./performance/scripts/run-k6.sh sustained10k | tee performance/results/$RUN_ID/sustained10k.log
./performance/scripts/run-k6.sh burst20k     | tee performance/results/$RUN_ID/burst20k.log

# Step 3 — Soak (overnight)
nohup ./performance/scripts/run-k6.sh soak8h > performance/results/$RUN_ID/soak8h.log &

# Step 4 — Settlement benchmark
./performance/settlement/run_settlement_benchmark.sh 500000 \
  | tee performance/results/$RUN_ID/settlement500k.log

# Step 5 — Capture infra metrics ระหว่างรัน
# - Grafana dashboard snapshot
# - kubectl top pods --containers
# - pg_stat_statements + Active connections
# - Kafka consumer lag
```

### Files ที่ต้องสร้าง (ถ้ายังไม่มี)
- [performance/scenarios/sustained-10k-tps.js](../performance/scenarios/sustained-10k-tps.js) — constant-arrival-rate 10K TPS for 60 นาที
- [performance/scenarios/burst-20k-tps.js](../performance/scenarios/burst-20k-tps.js) — ramping-arrival-rate 10K→20K TPS, hold 15 นาที
- [performance/scenarios/soak-8h.js](../performance/scenarios/soak-8h.js) — constant-arrival-rate 5K TPS for 8h
- ใช้ arrival-rate executor ไม่ใช้จำนวน VU เป็นตัวแทน TPS:
  ```js
  import http from 'k6/http';
  export const options = {
    scenarios: {
      sustained: {
        executor: 'constant-arrival-rate',
        rate: 10000,
        timeUnit: '1s',
        duration: '60m',
        preAllocatedVUs: 5000,
        maxVUs: 20000,
      },
    },
    thresholds: {
      http_req_duration: ['p(95)<500'],
      http_req_failed: ['rate<0.001'],
    },
  };
  export default function () {
    http.post(`${__ENV.BASE_URL}/api/transfers`, JSON.stringify({...}), {
      headers: { 'Content-Type': 'application/json', 'X-API-Key': __ENV.API_KEY }
    });
  }
  ```

### Evidence to collect
```
performance/results/$RUN_ID/
├── smoke.log
├── sustained10k.log
├── burst20k.log
├── soak8h.log
├── settlement500k.log
├── grafana-snapshots/
├── infra-metrics.csv
└── PERFORMANCE_SIGN_OFF.md  ← ผู้รับผิดชอบเซ็น
```

### Definition of Done
- ทุก scenario pass criteria ผ่าน
- Evidence bundle เก็บใน `performance/results/$RUN_ID/`
- Perf Lead เซ็น `PERFORMANCE_SIGN_OFF.md`
- Capacity plan ระบุ vertical scaling threshold (CPU > 70% → add node)

---

## P0.4 — Backup / PITR + DR Drill

### Why critical
BRD 11.1 บังคับ RTO ≤ 5 min, RPO ≤ 5 min — ต้องมี evidence ก่อน go-live

### Drills ที่ต้องรัน

| Drill | RTO target | RPO target | Script |
|---|---|---|---|
| **Full backup + verify** | n/a | n/a | `backup/bin/full-backup.sh` |
| **Restore drill (point-in-time)** | < 30 นาที | < 5 นาที | `backup/bin/restore-drill.sh` |
| **DR — pod kill** | < 5 นาที | 0 | `dr/scripts/run-dr-suite.sh pod-kill` |
| **DR — Kafka broker fail** | < 5 นาที | 0 (outbox replay) | `dr/scripts/run-dr-suite.sh kafka-fail` |
| **DR — network partition** | < 5 นาที | 0 | `dr/scripts/run-dr-suite.sh net-partition` |
| **DR — S3 down** | functional degradation | 0 | `dr/scripts/run-dr-suite.sh s3-down` |
| **DR — external API timeout** | retry + DLQ | 0 | `dr/scripts/run-dr-suite.sh ext-timeout` |
| **DR — region failover (ถ้ามี)** | < 5 นาที | < 5 นาที | `dr/scripts/run-dr-suite.sh region-failover` |

### Step-by-step

```bash
# Step 1 — Establish baseline (รันบน UAT ที่มีข้อมูล realistic)
./backup/bin/full-backup.sh
./backup/bin/verify-backup.sh

# Step 2 — Restore drill บน fresh DB
docker compose -f docker-compose.dr-test.yml up -d postgres-dr-test
./backup/bin/restore-drill.sh --target-time "1 hour ago"

# Step 3 — Verify row count + checksum match
PRIMARY_HASH=$(psql -h postgres -U switching -d switching_db -t -c "SELECT md5(string_agg(id::text, ',' ORDER BY id)) FROM transactions WHERE created_at > now() - interval '1 hour'")
RESTORE_HASH=$(psql -h postgres-dr-test -U switching -d switching_db -t -c "...")
[ "$PRIMARY_HASH" = "$RESTORE_HASH" ] && echo "MATCH ✅" || echo "DIVERGE ❌"

# Step 4 — DR drills (รันทีละตัว, capture metrics ก่อน/หลัง)
for scenario in pod-kill kafka-fail net-partition s3-down ext-timeout; do
  echo "=== $scenario ==="
  ./dr/scripts/run-dr-suite.sh $scenario
  ./dr/scripts/verify-recovery.sh
done

# Step 5 — Capture evidence
mkdir -p dr/evidence/$(date +%Y%m%d)
cp dr/scripts/output/* dr/evidence/$(date +%Y%m%d)/
```

### Evidence to collect
- Backup file SHA-256 + size + verify log
- Restore drill RPO/RTO actuals
- DR drill: log of failure injection + recovery time + tx loss check
- `DR_SIGN_OFF.md`

### Definition of Done
- ทุก drill achieve targets
- Evidence bundle ใน `dr/evidence/$(date)/`
- SRE Lead เซ็น `DR_SIGN_OFF.md`
- Runbook updated ถ้าเจอ edge case

---

## P0.5 — SMOS User & Access Management

### Why critical
BRD Ch 7.6 / BR-SMOS-014/15/16/17/18 — Operator (Admin, Settlement Officer, Risk Officer, etc.) ต้อง login เข้ามาจัดการระบบได้ ปัจจุบันมีแค่ API key

### Scope (Minimum Viable for Go-Live)

| Capability | ต้องมี |
|---|---|
| User CRUD | ✅ |
| 8 roles (System Admin, Ops Admin, Settlement Officer, Dispute Officer, Risk Officer, Auditor, Participant Admin, Read-Only) | ✅ |
| Login (username/password + MFA) | ✅ |
| RBAC enforcement at endpoint level | ✅ |
| Maker-checker workflow (2-person approval) สำหรับ sensitive actions | ✅ |
| Audit trail | ✅ |
| Self-service password reset | 🟡 P1 |
| SSO/SAML | 🟡 P1 |

### Files to create

```
src/main/java/com/example/switching/usermgmt/
├── entity/
│   ├── UserEntity.java                  -- id, username, password_hash, status, mfa_secret, last_login
│   ├── RoleEntity.java                  -- id, name (enum), description
│   ├── UserRoleEntity.java              -- user_id, role_id (composite key)
│   ├── PermissionEntity.java            -- id, resource, action (e.g., 'settlement.approve')
│   ├── RolePermissionEntity.java        -- role_id, permission_id
│   └── MakerCheckerRequestEntity.java   -- request_type, payload_json, maker_id, checker_id, status
├── repository/
│   ├── UserRepository.java
│   ├── RoleRepository.java
│   └── MakerCheckerRequestRepository.java
├── service/
│   ├── UserManagementService.java       -- create/update/disable user, assign role
│   ├── AuthenticationService.java       -- login, MFA verify, JWT issue
│   ├── AuthorisationService.java        -- check permission
│   └── MakerCheckerService.java         -- submit-for-approval, approve, reject
├── controller/
│   ├── UserController.java              -- /api/admin/users
│   ├── AuthController.java              -- /api/auth/login, /api/auth/mfa
│   └── MakerCheckerController.java      -- /api/admin/requests/{id}/approve
└── enums/
    └── RoleType.java                    -- 8 roles

src/main/resources/db/migration/
└── V97__smos_user_management.sql        -- users, roles, user_roles, permissions, maker_checker_requests
```

### Step-by-step

#### Step 1: Migration V97
```sql
-- V97__smos_user_management.sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(128) NOT NULL,
    email VARCHAR(128) NOT NULL UNIQUE,
    full_name VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    mfa_secret VARCHAR(64),
    failed_login_count INTEGER NOT NULL DEFAULT 0,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_user_status CHECK (status IN ('ACTIVE','LOCKED','DISABLED'))
);

CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(32) NOT NULL UNIQUE,
    description VARCHAR(256)
);

INSERT INTO roles (name, description) VALUES
    ('SYSTEM_ADMIN', 'Full administrative access'),
    ('OPS_ADMIN', 'Operations administration'),
    ('SETTLEMENT_OFFICER', 'Settlement queue + approval'),
    ('DISPUTE_OFFICER', 'Dispute case management'),
    ('RISK_OFFICER', 'Risk + AML investigation'),
    ('AUDITOR', 'Read-only across all domains + audit logs'),
    ('PARTICIPANT_ADMIN', 'Manage own participant config'),
    ('READ_ONLY', 'View dashboards only');

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles (id) ON DELETE RESTRICT,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    granted_by BIGINT REFERENCES users (id),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE permissions (
    id BIGSERIAL PRIMARY KEY,
    resource VARCHAR(64) NOT NULL,
    action VARCHAR(32) NOT NULL,
    description VARCHAR(256),
    CONSTRAINT uq_permission_res_action UNIQUE (resource, action)
);

CREATE TABLE role_permissions (
    role_id BIGINT NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES permissions (id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE maker_checker_requests (
    id BIGSERIAL PRIMARY KEY,
    request_type VARCHAR(64) NOT NULL,
    payload_json JSONB NOT NULL,
    payload_sha256 VARCHAR(64) NOT NULL,
    maker_id BIGINT NOT NULL REFERENCES users (id),
    checker_id BIGINT REFERENCES users (id),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_at TIMESTAMPTZ,
    decision_notes VARCHAR(512),
    CONSTRAINT ck_mc_status CHECK (status IN ('PENDING','APPROVED','REJECTED','EXPIRED','CANCELLED')),
    CONSTRAINT ck_mc_different_maker_checker CHECK (checker_id IS NULL OR checker_id <> maker_id)
);

CREATE INDEX idx_users_username ON users (username);
CREATE INDEX idx_mc_pending ON maker_checker_requests (status, submitted_at) WHERE status = 'PENDING';
```

#### Step 2: Authentication flow
```
1. POST /api/auth/login        { username, password }
   → 200 { mfa_required: true, mfa_token: "...", expires_in: 60 }
   หรือ 200 { jwt: "...", refresh_token: "...", expires_in: 3600 }
2. POST /api/auth/mfa          { mfa_token, totp_code }
   → 200 { jwt, refresh_token }
3. POST /api/auth/refresh      { refresh_token }
4. POST /api/auth/logout       (revoke refresh_token)
```

#### Step 3: RBAC enforcement
- Spring Security `@PreAuthorize("hasPermission('settlement', 'approve')")` on controller methods
- Custom `PermissionEvaluator` checks user → role → permission cache

#### Step 4: Maker-checker
- เมื่อ user ต้องการทำ action (e.g., approve settlement net position) → POST `/api/admin/requests` with payload
- ระบบสร้าง MakerCheckerRequest status = PENDING
- Checker (อีกคนที่มี role permission) GET `/api/admin/requests?status=PENDING`
- Approve: POST `/api/admin/requests/{id}/approve` — ระบบ execute action จริง
- ห้าม maker = checker (DB constraint)

#### Step 5: Permission seed data
```sql
INSERT INTO permissions (resource, action, description) VALUES
    ('settlement', 'view', 'View settlement queue'),
    ('settlement', 'approve', 'Approve net settlement (maker-checker)'),
    ('dispute', 'view', 'View dispute cases'),
    ('dispute', 'resolve', 'Resolve dispute (maker-checker)'),
    ('participant', 'manage', 'Manage participant (maker-checker)'),
    ('risk', 'investigate', 'Investigate risk alerts'),
    ('user', 'manage', 'Manage users (SYSTEM_ADMIN only)'),
    ('audit', 'read', 'Read audit logs (AUDITOR + SYSTEM_ADMIN)'),
    -- ... อีก ~30 permission อื่น
;

-- Then INSERT role_permissions mappings per role
```

### Definition of Done
- 8 roles seeded
- Login flow + MFA ทำงาน
- RBAC enforced on all admin endpoints
- Maker-checker workflow ทดสอบกับ 1 sensitive action (settlement approve)
- Integration tests cover: login fail, MFA verify, RBAC reject, maker=checker reject, approve flow
- API documented in OpenAPI

---

# P1 — Should-Have (ทำคู่กับ P0 ได้)

## P1.6 — Dashboard Suite (3 critical)

### Why
BRD 8.1 ระบุ 7 dashboards แต่ Go-Live ใช้แค่ 3 ตัวก็ผ่าน UAT ได้
- **Settlement Dashboard** — operator ต้องเห็น pending nets + SLA
- **Risk Dashboard** — Risk Officer ต้องดู alerts queue
- **Cross-Border Dashboard** — corridor availability + adapter status

### Implementation pattern (เหมือนกันทุก dashboard)

```
src/main/java/com/example/switching/dashboard/{settlement,risk,crossborder}/
├── controller/
│   └── XxxDashboardController.java      -- @GetMapping("/api/dashboard/xxx")
├── dto/
│   └── XxxDashboardResponse.java        -- KPIs + lists + trends
└── service/
    └── XxxDashboardService.java         -- aggregate from existing repositories
```

### Settlement Dashboard fields
- Pending net positions count + total amount
- Settlement cycles today (status per cycle)
- Failed settlements (last 7 days)
- Top participants by net debit/credit
- SLA: cycles closed on time / late
- Recent approvals (maker-checker)

### Risk Dashboard fields
- Active alerts (count by severity)
- Velocity threshold violations (24h)
- Fraud scoring trends
- Sanctions hits (pending review)
- Case aging buckets
- Top risk participants

### Cross-Border Dashboard fields
- Adapter status (Bakong, NAPAS, UPI, NITMX)
- Today volume by corridor
- FX rates current snapshot
- Failed rail messages (last 1h, last 24h)
- Reconciliation status per corridor

### Effort
- 4–5 วันต่อ dashboard (controller + service + DTO + 1 IT) × 3 = **12–15 วัน**

### Definition of Done
- 3 endpoints work + return realistic data
- RBAC enforce (SETTLEMENT_OFFICER เห็นเฉพาะ settlement, etc.)
- 1 integration test ต่อ dashboard
- Frontend hook ready (return shape stable)

---

## P1.7 — Promotion Eligibility DSL

### Why
Phase II merged โครงสร้าง promotion ทั้งหมดแต่ `PromotionEligibilityEvaluator.evaluate()` return false ถาวร → feature ใช้งานไม่ได้

### Recommended DSL: SpEL (Spring Expression Language)

ใช้ที่มีอยู่ใน Spring แล้ว ไม่ต้องเขียน parser เอง

### Rule structure (เก็บใน promotion_eligibility_rule table)
```yaml
promotion_id: 42
expression: "transaction.amount > 100000 AND payer.tier == 'GOLD' AND channel == 'QR'"
priority: 10
```

### Implementation skeleton

```java
@Service
public class PromotionEligibilityEvaluator {
    private final ExpressionParser parser = new SpelExpressionParser();
    private final Cache<Long, Expression> expressionCache = ...;

    public List<PromotionApplication> evaluate(PaymentContext ctx) {
        List<PromotionEligibilityRule> rules = repo.findActiveByContext(ctx);
        return rules.stream()
            .filter(rule -> {
                Expression expr = expressionCache.get(rule.getId(),
                    k -> parser.parseExpression(rule.getExpression()));
                StandardEvaluationContext sec = new StandardEvaluationContext();
                sec.setVariable("transaction", ctx.transaction());
                sec.setVariable("payer", ctx.payer());
                sec.setVariable("payee", ctx.payee());
                sec.setVariable("channel", ctx.channel());
                return Boolean.TRUE.equals(expr.getValue(sec, Boolean.class));
            })
            .sorted(Comparator.comparingInt(PromotionEligibilityRule::getPriority).reversed())
            .map(rule -> applyPromotion(ctx, rule.getPromotion()))
            .toList();
    }
}
```

### Security note
SpEL is **executable code**. ห้ามให้ external user เขียน expression ตรงๆ — ผ่าน maker-checker approval flow + whitelist allowed variables.

### Definition of Done
- 5 sample promotions seeded + evaluated correctly
- Priority ordering ทำงาน
- Budget cap enforced (use advisory lock จาก settlement pattern)
- Funder ledger debit/credit ลง `promotion_settlement` table
- Integration tests: full match, partial match, budget exhausted, priority tiebreak

### Alternative — ถ้าไม่ใช้ promotion: feature flag off
```yaml
# application.yml
switching:
  promotion:
    enabled: ${PROMOTION_ENABLED:false}
```
แล้ว `@ConditionalOnProperty` skip ทั้ง package — เร็วกว่า แต่ไม่ได้ feature

---

# P2 — Nice-to-Have (เลื่อนได้)

## P2.8 — WhatsApp Notification Channel

ใช้ Twilio WhatsApp Business API หรือ AWS SNS — ขอ business verification ก่อน (2–3 สัปดาห์ wait time)

**Workaround:** ส่ง alert ผ่าน Email + Dashboard ไปก่อน

## P2.9 — One-click DR Switchover UI

Workaround: SRE ใช้ Runbook `docs/runbooks/DR_FAILOVER.md` (ลบไปแล้ว — ต้อง recreate) + Ansible playbook

---

# 📋 Cross-Cutting Tasks

## Each P0 task ต้อง

1. ✅ Migration เพิ่ม (ถ้ามี schema) ใช้ V97+
2. ✅ Test ≥ 1 unit + 1 integration test
3. ✅ Update [docs/GO_LIVE_CRITICAL_PATH.md](GO_LIVE_CRITICAL_PATH.md) status
4. ✅ ผ่าน `./scripts/execute-and-verify/00-run-all.sh`
5. ✅ Static verifier เขียว

## Migration version reservations

| Version | Purpose | Status |
|---|---|---|
| V88–V90 | reserved for future read-scaling | ⚪ |
| V91–V96 | Phase II (RTP, Promotion, Push, Reporting, Cross-Border, RTP-ext) | 🟢 done |
| **V97** | SMOS user management | 🟡 implemented; runtime verification pending |
| **V98** | Dashboard aggregation views (ถ้าต้อง materialise) | ⚪ reserved |
| **V99** | Promotion DSL rules table extension | ⚪ reserved |
| **V100** | Current-status reporting repair and rebuild | 🟡 implemented; runtime verification pending |
| **V101** | SMOS session/participant security hardening | 🟡 implemented; runtime verification pending |
| V102–V103 | reserved | ⚪ |
| **V104** | financial precision standardisation | 🟡 implemented; migration certification pending |
| **V105** | promotion budget/funder ledger controls | 🟡 implemented; promotion remains disabled by default |
| **V106** | durable distributed trace correlation | 🟡 implemented; OTLP UAT verification pending |

---


# Phase 62 — Repository Operational Hardening

- [x] 62A static regression guards for the four historical test blockers
- [~] 62B strict full verification (`mvn verify` not executed in isolated implementation environment)
- [x] 62C SMOS permission matrix, OpenAPI and admin endpoint guard verifier
- [~] 62D transaction-aware read-replica routing; UAT `pg_is_in_recovery()` evidence pending
- [~] 62E V104 `NUMERIC(24,4)` policy; clean/upgrade migration test pending
- [x] 62F Hikari metrics, alerts, Grafana dashboard and runbook
- [~] 62G critical dashboard hardening; UAT latency/data acceptance pending
- [~] 62H V105 promotion budget/ledger controls; Product enablement decision pending
- [~] 62I N+1 SQL fingerprint diagnostics and bounded pagination; UAT audit pending
- [~] 62J V106 trace persistence and OTLP configuration; end-to-end Tempo evidence pending

Strict Phase 62 certification requires the authoritative Phase II migrations V91–V96.
The supplied implementation baseline did not contain those files; they must be merged
from the approved branch before running the strict repository or migration gate.


# 🚀 Timeline

```
Week 1 ─┬─ P0.1 mvn verify fix     (1 dev, 3 hrs)
        ├─ P0.2 secret rotation    (SecOps + 1 dev, 6 hrs)
        ├─ P0.3 perf test setup    (1 dev, start)
        └─ P0.5 SMOS user mgmt     (1 dev, start)
Week 2 ─┬─ P0.3 perf test run      (UAT environment)
        ├─ P0.4 backup/DR drill    (1 SRE, 5 days)
        ├─ P0.5 SMOS continuation  (continue)
        └─ P1.6 dashboard (start parallel)
Week 3 ─┬─ P0.5 SMOS finalise + integration
        ├─ P1.6 dashboard finalise
        ├─ P1.7 promotion DSL      (1 dev)
        └─ Phase 54 UAT certification kick-off
```

**Total calendar: 3 สัปดาห์** (assuming 2–3 engineers + 1 SRE + 1 SecOps)

---

# 🎯 Go/No-Go Gates

| Gate | Date | ผู้ตัดสินใจ | เกณฑ์ |
|---|---|---|---|
| **Tag RC** | End of week 2 | Eng Lead | P0.1 ผ่าน + P0.5 SMOS API ready |
| **UAT entry** | Start of week 3 | QA Lead | P0.3 + P0.4 evidence ผ่าน |
| **Production canary** | End of week 3 | Change Manager | Phase 54 ทุก step + P0.2 rotated |
| **100% cutover** | +1 day | Ops Lead | Reconciliation match บน canary |
| **Go-Live sign-off** | +14 day hypercare | Business + Ops + Security | ไม่มี P1 incident |

---

# 📞 Open Questions for BoL / Product

ตอบก่อนเริ่ม implement P0:

1. **MFA method** — TOTP (Google Authenticator) หรือ SMS หรือ FIDO2?
2. **Performance SLA strict?** — ถ้า P95 = 510ms (เกิน 500 เล็กน้อย) ต้องเลื่อน go-live ไหม?
3. **DR RPO** — ยอม 5 นาที หรือต้อง 0 (synchronous replication)?
4. **SMOS roles** — ต้องตรงกับ 8 roles ที่ระบุใน BRD หรือ scheme operator กำหนดเองได้?
5. **Promotion launch** — ใช้ตั้งแต่ go-live หรือเปิดทีหลัง (feature flag)?

---

*Single source of truth — update inline เมื่อมีอะไรเปลี่ยน*
