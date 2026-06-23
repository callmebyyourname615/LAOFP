# Go-Live Critical Path — Master Checklist

> **Last updated:** 2026-06-22
> **Audience:** Engineering, QA, SecOps, SRE, Change Manager
> **Purpose:** Single checklist สำหรับพา Switching ขึ้น production ครบทุกมิติ
> **Convention:**
> - `[x]` = done & evidence on file
> - `[~]` = code done / runtime evidence pending
> - `[ ]` = not started
> - `[-]` = N/A / deferred

---

## 🟢 Quick Snapshot

| Section | Progress | Note |
|---|---|---|
| 1. Code Foundation | 🟢 **100%** | 95 migrations, Phase II + Golive merged |
| 2. P0 Critical Fixes | 🟡 **60%** | code done; runtime + ops actions pending |
| 3. P1 Operational | 🟡 **70%** | 3 dashboards done, DSL done, WhatsApp + DR UI pending |
| 4. P2 Nice-to-have | 🔴 **0%** | deferred |
| 5. Runtime Evidence | 🔴 **0%** | no UAT drill executed yet |
| 6. Operator Actions | 🔴 **0%** | secrets not rotated |
| 7. UAT Certification | 🔴 **0%** | Phase 54 not started |
| 8. Production Go-Live | 🔴 **0%** | Phase 55 not started |
| 9. Post Go-Live BAU | 🔴 **0%** | hypercare not started |
| **OVERALL** | **🟡 ~70%** | **2–3 weeks to Go-Live** |

---

# 1. Code Foundation (🟢 Complete)

- [x] Phase 1–22 core payment processing (V1–V32)
- [x] Phase 23–32 hardening (V33–V42)
- [x] Phase 33–42 governance (V43–V72)
- [x] Phase 43–52 advanced governance (V73–V82)
- [x] Phase 53A Repository Security Cleanup
- [x] Phase 53B V83 schema alignment + V84 follow-up (all SHA-256 → VARCHAR)
- [x] Phase 53C–53J Production Hardening
- [x] Phase 54A–54J certification framework
- [x] Phase 55A–55J go-live framework
- [x] Phase 56A–56J Day-2 operations
- [x] Phase 57A–57J Enterprise maturity
- [x] Phase 58A–58J Regulatory ecosystem
- [x] DB read-scaling migrations (V85–V87 user's own work)
- [x] Phase II RTP (V91, V96)
- [x] Phase II Promotion (V92)
- [x] Phase II Push Orchestrator (V93)
- [x] Phase II Scheduled Report Delivery (V94)
- [x] Phase II Cross-Border Rail Journal (V95)
- [x] V97 SMOS user & access management
- [x] V100 status reporting repair
- [x] `./mvnw compile` passes
- [x] Dockerfile + compose syntax validate

**Migration sequence:** V1–V100 (gaps V88–V90, V98–V99 reserved) — **95 migrations contiguous**

---

# 2. P0 Critical Fixes — MUST DO before Go-Live

## P0.1 — Fix `mvn verify` test failures

- [~] Update Flyway version assertions to V100 / count 95L (3 ไฟล์)
  - [x] `V83CleanInstallCertificationIntegrationTest`
  - [x] `V83PayloadSha256SchemaAlignmentIntegrationTest`
  - [x] `MigrationApplicationIntegrationTest`
- [x] Update static verifiers to expect V100 (verify_phase53b, 53c-j, 43-52, 54a-j)
- [ ] Fix vault `ObjectMapper` missing in `WebhookEncryptionConfiguration`
- [ ] Add `provider_uid` seed in `SanctionsScreeningIntegrationTest`
- [ ] Fix FK cleanup order in `OperationsGenerateRoutesForBankIntegrationTest`
- [ ] Fix `setObject(Instant)` → `setObject(idx, instant, Types.TIMESTAMP_WITH_TIMEZONE)` in cross-border
- [ ] **Run `./mvnw verify` → 0 errors, 0 failures**
- [ ] **Run `./scripts/execute-and-verify/00-run-all.sh` → all 5 steps green**

**Effort:** 3 ชม.   **Owner:** 1 dev   **Status:** 🟡 code อัปเดต / ต้องรันยืนยัน

---

## P0.2 — Secret rotation + git history purge

- [x] `security/scripts/purge-sensitive-history.sh` ready
- [x] `docs/security/SECRET_ROTATION_CHECKLIST.md` reconstituted by Phase 60
- [x] `.env.prod.example` purged of `change_me`
- [ ] Generate 6 new strong passwords (32 chars random)
- [ ] Store new credentials in Vault prod `secret/switching/prod`
- [ ] Freeze repo writes (announce 30-min window)
- [ ] Run `purge-sensitive-history.sh --execute` on mirror clone
- [ ] Force-push purged history to origin
- [ ] Invalidate GitHub Actions caches
- [ ] Notify all team to re-clone
- [ ] Rotate service-account tokens used by repo
- [ ] Verify `git log --all --grep "change_me"` returns nothing
- [ ] Sign `SECRET_ROTATION_CHECKLIST.md` (SecOps + Repo coordinator)

**Effort:** 6 ชม.   **Owner:** SecOps + Repo coordinator   **Status:** 🔴 ops action pending

---

## P0.3 — Performance proof: 10K TPS + 20K burst

- [x] k6 smoke scenario (`performance/scenarios/smoke.js`)
- [x] k6 sustained-2k (`sustained-2k-tps.js`)
- [x] k6 sustained-10k
- [x] k6 burst-20k
- [x] k6 soak-8h (`soak-8h.js`)
- [x] k6 settlement-500k (`performance/settlement/run_settlement_benchmark.sh`)
- [x] `performance/scripts/run-k6.sh` orchestrator
- [ ] UAT environment provisioned (4 app pods, Postgres r6g.4xlarge, Kafka 3-broker)
- [ ] Run smoke scenario → P95 < 200ms, error rate = 0%
- [ ] Run sustained 2K → P95 < 300ms, error < 0.1%
- [ ] Run sustained 10K (60 min) → **P95 < 500ms** (BRD ACC-026)
- [ ] Run burst 20K (15 min) → error < 0.5%, no connection exhaustion
- [ ] Run soak 8h → no memory leak, GC < 100ms, no Kafka lag growth
- [ ] Run settlement 500k benchmark
- [ ] Capture Grafana snapshots ระหว่างรัน
- [ ] Capture `pg_stat_statements`, `kubectl top pods`, Kafka lag
- [ ] Create capacity plan (vertical scaling threshold)
- [ ] Sign `PERFORMANCE_SIGN_OFF.md` by Perf Lead

**Effort:** 12–15 วัน   **Owner:** QA + 1 dev   **Status:** 🟡 code ready / UAT pending

---

## P0.4 — Backup / PITR + DR drill

- [x] `backup/bin/full-backup.sh`, `verify-backup.sh`, `restore-drill.sh`
- [x] `dr/scripts/run-dr-suite.sh` with 6 scenarios
- [x] `dr/scripts/verify-recovery.sh`
- [ ] Full backup on UAT + verify SHA-256 + row count
- [ ] Restore drill → RPO < 5 min, RTO < 30 min
- [ ] DR — pod kill scenario
- [ ] DR — Kafka broker fail scenario
- [ ] DR — network partition scenario
- [ ] DR — S3 down scenario
- [ ] DR — external API timeout scenario
- [ ] DR — region failover (ถ้า multi-region)
- [ ] Verify no transaction loss + outbox replay idempotent
- [ ] Capture all evidence in `dr/evidence/<date>/`
- [ ] Sign `DR_SIGN_OFF.md` by SRE Lead

**Effort:** 5 วัน   **Owner:** 1 SRE   **Status:** 🟡 code ready / UAT pending

---

## P0.5 — SMOS User & Access Management

- [x] V97 migration: `users`, `roles`, `user_roles`, `permissions`, `role_permissions`, `maker_checker_requests`
- [x] 8 roles seeded (SYSTEM_ADMIN, OPS_ADMIN, SETTLEMENT_OFFICER, DISPUTE_OFFICER, RISK_OFFICER, AUDITOR, PARTICIPANT_ADMIN, READ_ONLY)
- [x] `UserManagementService` CRUD
- [x] `TotpService` MFA implementation
- [x] `SmosTokenService` JWT issue + refresh
- [x] `MakerCheckerService` 2-person approval
- [x] `SettlementApprovalActionHandler` (sample maker-checker target)
- [x] RBAC enforcement via Spring Security
- [x] `SmosUserManagementIntegrationTest`
- [x] `TotpServiceTest`, `SmosTokenServiceTest`
- [x] `SmosSecurityCertificationIntegrationTest` (Phase 60)
- [ ] Run integration suite → all green on UAT
- [ ] Seed 5 initial admin users
- [ ] Document RBAC permission matrix
- [ ] OpenAPI spec for `/api/auth/*` and `/api/admin/users/*`
- [ ] Lock all admin endpoints behind RBAC (audit grep `@PreAuthorize`)

**Effort:** 20 วัน (DONE in merge)   **Owner:** 1 dev   **Status:** 🟢 code done

---

# 3. P1 Operational Readiness

## P1.6 — Dashboard Suite

- [x] Settlement Dashboard (`/api/dashboard/settlement`)
- [x] Risk Dashboard (`/api/dashboard/risk`)
- [x] Cross-Border Dashboard (`/api/dashboard/cross-border`)
- [x] `CriticalDashboardIntegrationTest`
- [ ] Transaction Dashboard (BRD 8.1)
- [ ] Participant Dashboard (BRD 8.1)
- [ ] Infrastructure Dashboard (BRD 8.1)
- [ ] DR Dashboard (BRD 8.1)
- [ ] Verify RBAC: each dashboard restricted to relevant role
- [ ] Frontend hook into SMOS Lovable portal

**Effort:** 15 วัน (3/7 done, 4 remain optional for go-live)   **Owner:** 1 dev   **Status:** 🟡 partial

---

## P1.7 — Promotion Eligibility DSL

- [x] JSON allowlist DSL evaluator
- [x] `PromotionEligibilityEvaluatorTest`
- [x] Feature flag `switching.promotion.enabled` (default off)
- [ ] Decision: enable at Go-Live? (Product team)
- [ ] Seed 3 sample promotions for UAT
- [ ] Budget cap enforcement test under concurrent load
- [ ] Funder ledger reconciliation report

**Effort:** 15 วัน (DONE)   **Owner:** 1 dev   **Status:** 🟢 code done

---

# 4. P2 Nice-to-Have (Deferred)

## P2.8 — WhatsApp Notification Channel
- [ ] Apply for Twilio WhatsApp Business or AWS SNS verification (2–3 week wait)
- [ ] Implement `WhatsAppDeliveryService`
- [ ] Add to `NotificationChannelRouter`
- [ ] Template management UI

**Workaround for Go-Live:** Use Email + Dashboard alerts only

## P2.9 — One-Click DR Switchover UI
- [ ] DR controller + service
- [ ] Failover automation
- [ ] Replication lag monitor
- [ ] Rollback UI

**Workaround for Go-Live:** SRE uses runbook + Ansible playbook

---

# 5. Runtime Evidence (🔴 0%)

| Drill | Status | Owner |
|---|---|---|
| [ ] Full test suite (`./mvnw verify`) | 🔴 | Dev |
| [ ] k6 smoke | 🔴 | QA |
| [ ] k6 sustained 2K (30 min) | 🔴 | QA |
| [ ] k6 sustained 10K (60 min) | 🔴 | QA |
| [ ] k6 burst 20K (15 min) | 🔴 | QA |
| [ ] k6 soak 8h | 🔴 | QA |
| [ ] Settlement 500K benchmark | 🔴 | QA |
| [ ] Backup + verify | 🔴 | SRE |
| [ ] Restore drill | 🔴 | SRE |
| [ ] DR pod kill | 🔴 | SRE |
| [ ] DR Kafka fail | 🔴 | SRE |
| [ ] DR network partition | 🔴 | SRE |
| [ ] DR S3 down | 🔴 | SRE |
| [ ] DR external timeout | 🔴 | SRE |
| [ ] Sanctions sync mock | 🔴 | Compliance |
| [ ] Vault key rotation drill | 🔴 | SecOps |
| [ ] Alert firing 47 alerts | 🔴 | SRE |

**Exit:** Evidence bundle เก็บใน `scripts/execute-and-verify/evidence/<run-id>/`

---

# 6. Operator Actions (🔴 0%)

- [ ] **SecOps:** Generate 6 new prod credentials (32-char random)
- [ ] **SecOps:** Store in Vault `secret/switching/prod`
- [ ] **Repo coordinator:** Freeze repo writes
- [ ] **Repo coordinator:** Run `purge-sensitive-history.sh --execute`
- [ ] **Repo coordinator:** Force-push purged history
- [ ] **All team:** Re-clone repo
- [ ] **SecOps:** Invalidate GitHub Actions caches
- [ ] **SecOps:** Rotate service-account tokens
- [ ] **SecOps + Repo coordinator:** Sign `SECRET_ROTATION_CHECKLIST.md`
- [ ] **Legal:** Confirm `.env.prod.example` has no leakage

---

# 7. Phase 54 — UAT Certification

รันบน UAT cluster — `scripts/certification/run_phase54_certification.sh`

- [ ] 54A Build & Test certification
- [ ] 54B Migration certification (V1→V100)
- [ ] 54C UAT deployment rehearsal
- [ ] 54D Performance & capacity (uses P0.3 evidence)
- [ ] 54E Settlement 500k
- [ ] 54F Backup / PITR (uses P0.4 evidence)
- [ ] 54G DR & failure recovery (uses P0.4 evidence)
- [ ] 54H Security & supply chain
- [ ] 54I Observability & alert
- [ ] 54J Go-Live rehearsal + RC assembly
- [ ] Sign signed certification manifest

---

# 8. Phase 55 — Production Go-Live

รันบน production runners — `scripts/golive/run_phase55_golive.sh`

- [ ] 55A Assemble immutable signed RC
- [ ] 55B Validate production infrastructure contract
- [ ] 55C Migration dry-run on production-like replica
- [ ] 55D Capture cutover financial baseline
- [ ] 55E RBAC / NetworkPolicy / secret hardening
- [ ] 55F Command center readiness + signed approvals
- [ ] 55G Canary 5%
- [ ] 55H Controlled cutover 25% → 50% → 100%
- [ ] 55I Hypercare validation
- [ ] 55J BAU acceptance + handover manifest
- [ ] Sign operational acceptance manifest → 🚀 GO-LIVE

---

# 9. Post Go-Live BAU (Hypercare 14 days)

- [ ] Day 1: 24/7 SRE coverage
- [ ] Day 1: Watch all 47 alerts firing as expected
- [ ] Day 1: Reconciliation match check (every cycle)
- [ ] Day 3: First daily settlement complete
- [ ] Day 7: First weekly recon clean
- [ ] Day 14: Hypercare exit review
- [ ] Day 14: Sign hypercare-exit acceptance
- [ ] Day 14: Hand over to standard ops

---

# 10. 🆕 Additional Scope (เพิ่มจาก audit ล่าสุด)

ที่ควรพิจารณาเพิ่ม **ก่อนหรือทันทีหลัง Go-Live**:

## 10.1 — Read Replica Routing (จาก DB audit)

- [ ] Add `RoutingDataSource` based on `@Transactional(readOnly=true)`
- [ ] Configure 2 HikariCP pools (primary 30 / replica 20)
- [ ] Mark dashboard queries + reports as `readOnly=true`
- [ ] Wrap with `LazyConnectionDataSourceProxy`
- [ ] Integration test `pg_is_in_recovery()` → routes correctly
- [ ] Document read-your-writes consistency policy

**Why:** ลด load primary 50–70% — ช่วยให้ 10K TPS test ผ่านง่ายขึ้น
**Effort:** 1 วัน

## 10.2 — Standardize NUMERIC precision (จาก DB audit)

- [ ] เลือก standard: `NUMERIC(24,4)` สำหรับ money
- [ ] Audit ทั้งหมด: `NUMERIC(19,4)`, `NUMERIC(18,2)`, `NUMERIC(20,2)` → migration เปลี่ยน
- [ ] Document precision policy ใน `docs/`
- [ ] Update JPA entity `BigDecimal precision = 24, scale = 4`

**Why:** กัน rounding edge case ตอน FX conversion + settlement netting
**Effort:** 0.5 วัน

## 10.3 — Hourly Partition Strategy (เผื่อ peak TPS > 5K)

- [ ] Build `precreate_hourly_partitions()` function (V101 reserved)
- [ ] Build `consolidate_hourly_to_daily()` function
- [ ] Add `@Scheduled` to call every hour (48h lookahead)
- [ ] Decide trigger: peak TPS > 5K from Phase 54D perf test

**Why:** ที่ 10K TPS = 36M rows/hour → partition รายวันจะมี 864M rows ต่อ partition (slow)
**Effort:** 3 วัน (ทำเมื่อ trigger)

## 10.4 — JPA Lazy Loading + N+1 Audit

- [ ] Enable `spring.jpa.properties.hibernate.SQL_INSPECT_NPLUS1=true` ใน UAT
- [ ] รัน performance smoke + check log for N+1 patterns
- [ ] Add `@EntityGraph` ที่จุดร้อน
- [ ] Confirm pagination ทุก list endpoint

**Why:** N+1 = TPS degrade 5-10× ตอน burst
**Effort:** 2 วัน

## 10.5 — Distributed Tracing (OpenTelemetry)

- [ ] Add `opentelemetry-spring-boot-starter`
- [ ] Configure OTLP exporter to Jaeger/Tempo
- [ ] Propagate trace IDs across Kafka outbox
- [ ] Add trace_id to audit log records
- [ ] Document trace-id-aware debug runbook

**Why:** debug 10K TPS production issues impossible without trace
**Effort:** 3 วัน

## 10.6 — Chaos Engineering Suite

- [ ] Install Chaos Mesh / LitmusChaos on UAT
- [ ] Define 10 scenarios: pod kill, network delay, disk full, time skew, DNS fail, etc.
- [ ] Run weekly during hypercare
- [ ] Document recovery actions per scenario

**Why:** Real-world failure modes ไม่เคยตรง runbook 100%
**Effort:** 5 วัน

## 10.7 — Open Banking / PSD2-style Consent (BRD future-scope)

- [ ] Consent record table (`V102` reserved)
- [ ] OAuth2 + PKCE consent flow
- [ ] Scope-based permission

**Why:** BRD mentions future regulator requirement
**Effort:** 15 วัน (Phase IV)

## 10.8 — Rate Limiting per participant

- [ ] Implement token-bucket rate limit (Redis or in-memory)
- [ ] Configure per-participant quotas
- [ ] 429 response with `Retry-After` header
- [ ] Dashboard panel showing throttle stats

**Why:** Protect from misbehaving participant flooding switch
**Effort:** 2 วัน

## 10.9 — Database Connection Pool Monitoring

- [ ] Expose HikariCP metrics to Prometheus
- [ ] Alert when pool > 80% utilization
- [ ] Alert when wait time > 100ms

**Why:** Connection exhaustion is silent killer at 10K TPS
**Effort:** 0.5 วัน

## 10.10 — Disaster Recovery Documentation Drill

- [ ] Print runbooks (paper copy in SRE office)
- [ ] Off-site backup of secrets vault (encrypted USB in safe)
- [ ] Quarterly DR drill scheduled
- [ ] Vendor escalation list (Postgres support, Kafka support, ฯลฯ)

**Why:** When everything is down คุณจะ google ไม่ได้
**Effort:** 1 วัน

---

# 📅 Timeline (Updated)

```
Week 1 ─┬─ P0.1 mvn verify fix      (Day 1, 3 hrs)
        ├─ P0.2 secret rotation      (Day 2, SecOps)
        ├─ Read replica routing      (Day 1, +1 day) [10.1]
        ├─ NUMERIC standardise       (Day 2, +0.5 day) [10.2]
        └─ HikariCP monitoring       (Day 2, +0.5 day) [10.9]
Week 2 ─┬─ P0.3 perf 10K/20K UAT    (5 days)
        ├─ P0.4 backup + DR drill   (5 days, parallel)
        ├─ JPA N+1 audit            (2 days) [10.4]
        └─ Distributed tracing      (3 days) [10.5]
Week 3 ─┬─ Phase 54 UAT certification (5 days)
        └─ Chaos engineering setup   (5 days, parallel) [10.6]
Week 4 ─┬─ Phase 55A-55F (RC + infra + hardening)
        └─ DR docs drill            (1 day) [10.10]
Week 5 ─┬─ Phase 55G-55H (canary → 100%)
        └─ Phase 55I hypercare
Week 6+ Phase 55J sign-off → 🚀 GO-LIVE + 14-day hypercare
```

**Total calendar: 5–6 สัปดาห์** (เพิ่ม additional scope) หรือ **3 สัปดาห์** (ทำ P0+P1 อย่างเดียว)

---

# 🎯 Decision Gates

| Gate | Date | Decision Maker | เกณฑ์ |
|---|---|---|---|
| Tag RC | End of week 2 | Eng Lead | P0.1 + P0.5 ✅, P0.2 in progress |
| UAT entry | Start of week 3 | QA Lead | P0.3 + P0.4 evidence ผ่าน |
| Production canary | End of week 4 | Change Manager | Phase 54 all + P0.2 done |
| 100% cutover | +1 day | Ops Lead | Reconciliation match บน canary |
| Go-Live sign-off | +14 day hypercare | Business + Ops + Security | ไม่มี P1 incident |

---

# 📞 Open Questions ที่ยังต้องตอบ

- [ ] MFA method: TOTP / SMS / FIDO2? — **Recommend TOTP (already implemented)**
- [ ] Performance SLA strict? — if P95 = 510ms (เกิน 500 เล็กน้อย) ต้องเลื่อน Go-Live ไหม?
- [ ] DR RPO: 5 นาที พอ หรือต้อง 0 (synchronous replication)?
- [ ] SMOS roles: ใช้ 8 roles ตาม BRD หรือ scheme operator กำหนดเองได้?
- [ ] Promotion launch: ใช้ตั้งแต่ Go-Live หรือเปิดทีหลัง? — **Recommend OFF at launch**
- [ ] Multi-region active-active หรือ active-standby?
- [ ] Public stats / open data: scope ไหน?
- [ ] Final Go-Live date target?

---

# 📊 Current State (auto-generated)

```yaml
migrations_total: 95
migrations_latest: V100
reserved_gaps: [V88, V89, V90, V98, V99]
code_coverage_brd: ~98%
critical_path_code_done: 100%
critical_path_runtime_done: 0%
mvn_compile: passing
mvn_verify: pending (assertions updated, awaiting rerun)
last_merge_commit: c988859 (Golive Critical Path + Phase 60A-60J)
```

---

*Single source of truth — update inline ทันทีเมื่อมีอะไรเปลี่ยน*
