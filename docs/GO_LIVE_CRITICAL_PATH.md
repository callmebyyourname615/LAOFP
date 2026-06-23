# Go-Live Critical Path — Master Checklist

> **Last updated:** 2026-06-23 (after Phase 61 merge — commit `f5a2453`)
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
| 1. Code Foundation | 🟢 **100%** | 96 migrations (V1–V101) |
| 2. P0 Critical Fixes | 🟡 **70%** | code done; runtime + ops pending |
| 3. P1 Operational | 🟢 **85%** | 3 dashboards done, DSL done, hardened |
| 4. P2 Nice-to-have | 🔴 **0%** | deferred |
| 5. Runtime Evidence | 🔴 **0%** | no UAT drill executed yet |
| 6. Operator Actions | 🔴 **0%** | secrets not rotated |
| 7. Phase 61 UAT Preflight | 🟢 **scripts ready** | not executed yet |
| 8. Phase 54 UAT Certification | 🔴 **0%** | depends on Phase 61 |
| 9. Phase 55 Production Go-Live | 🔴 **0%** | depends on Phase 54 |
| 10. Post Go-Live BAU | 🔴 **0%** | hypercare not started |
| **OVERALL** | **🟡 ~45%** | **2–3 weeks to Go-Live** |

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
- [x] DB read-scaling migrations (V85–V87)
- [x] Phase II RTP (V91, V96)
- [x] Phase II Promotion (V92)
- [x] Phase II Push Orchestrator (V93)
- [x] Phase II Scheduled Report Delivery (V94)
- [x] Phase II Cross-Border Rail Journal (V95)
- [x] V97 SMOS user & access management
- [x] V100 status reporting repair
- [x] V101 SMOS security hardening (Phase 61)
- [x] `./mvnw compile` passes
- [x] Dockerfile + compose syntax validate

**Migration sequence:** V1–V101 (gaps V88–V90, V98–V99 reserved) — **96 migrations contiguous**

---

# 2. P0 Critical Fixes — MUST DO before Go-Live

## P0.1 — Fix `mvn verify` test failures

- [x] Update Flyway version assertions to V100 / V101 (3 ไฟล์ updated)
- [x] Update static verifiers to expect V100/V101 (verify_phase53b, 53c-j, 43-52, 54a-j)
- [ ] Fix vault `ObjectMapper` missing in `WebhookEncryptionConfiguration`
- [ ] Add `provider_uid` seed in `SanctionsScreeningIntegrationTest`
- [ ] Fix FK cleanup order in `OperationsGenerateRoutesForBankIntegrationTest`
- [ ] Fix `setObject(Instant)` → `setObject(idx, instant, Types.TIMESTAMP_WITH_TIMEZONE)` in cross-border
- [ ] **Run `./mvnw verify` → 0 errors, 0 failures**
- [ ] **Run `./scripts/execute-and-verify/00-run-all.sh` → all 5 steps green**

**Effort:** 3 ชม.   **Owner:** 1 dev   **Status:** 🟡 assertion fixes done / 4 test bugs remain

---

## P0.2 — Secret rotation + git history purge

- [x] `security/scripts/purge-sensitive-history.sh` ready
- [x] `docs/security/SECRET_ROTATION_CHECKLIST.md` (Phase 60)
- [x] `.env.prod.example` purged of `change_me`
- [x] `config/production-environment-contract.yaml` defined
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
- [x] Phase 61G performance certification script (`scripts/phase61/61G-performance-capacity-certification.sh`)
- [x] Phase 61 capacity attestation template (`docs/templates/PHASE61_CAPACITY_ATTESTATION.example.json`)
- [ ] UAT environment provisioned (4 app pods, Postgres r6g.4xlarge, Kafka 3-broker)
- [ ] Run smoke scenario → P95 < 200ms, error rate = 0%
- [ ] Run sustained 2K → P95 < 300ms, error < 0.1%
- [ ] Run sustained 10K (60 min) → **P95 < 500ms** (BRD ACC-026)
- [ ] Run burst 20K (15 min) → error < 0.5%, no connection exhaustion
- [ ] Run soak 8h → no memory leak, GC < 100ms, no Kafka lag growth
- [ ] Run settlement 500k benchmark
- [ ] Capture Grafana snapshots
- [ ] Capture `pg_stat_statements`, `kubectl top pods`, Kafka lag
- [ ] Create capacity plan
- [ ] Sign `PERFORMANCE_SIGN_OFF.md`

**Effort:** 12–15 วัน   **Owner:** QA + 1 dev   **Status:** 🟡 code ready / UAT pending

---

## P0.4 — Backup / PITR + DR drill

- [x] `backup/bin/full-backup.sh`, `verify-backup.sh`, `restore-drill.sh`
- [x] `dr/scripts/run-dr-suite.sh` with 6 scenarios
- [x] `dr/scripts/verify-recovery.sh`
- [x] Phase 61I resilience drill script (`61I-resilience-alert-drills.sh`)
- [x] Phase 61 resilience attestation template
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
- [ ] Sign `DR_SIGN_OFF.md`

**Effort:** 5 วัน   **Owner:** 1 SRE   **Status:** 🟡 code ready / UAT pending

---

## P0.5 — SMOS User & Access Management

- [x] V97 migration: users, roles, user_roles, permissions, role_permissions, maker_checker_requests
- [x] V101 SMOS security hardening (password policy, session security)
- [x] 8 roles seeded
- [x] `UserManagementService` CRUD
- [x] `TotpService` MFA implementation
- [x] `SmosTokenService` JWT issue + refresh + session entity
- [x] `SmosBootstrapAdminRunner` initial admin seed
- [x] `MakerCheckerService` 2-person approval
- [x] `SettlementApprovalActionHandler`
- [x] `SmosJwtAuthenticationFilter` (Phase 61 hardened)
- [x] `PasswordPolicyService` (Phase 61)
- [x] `AuthSessionEntity` + repository (Phase 61)
- [x] RBAC enforcement via Spring Security
- [x] `SmosUserManagementIntegrationTest`
- [x] `TotpServiceTest`, `SmosTokenServiceTest`
- [x] `SmosSecurityCertificationIntegrationTest` (Phase 60)
- [x] `PasswordPolicyServiceTest` (Phase 61)
- [x] `SmosSessionSecurityIntegrationTest` (Phase 61)
- [x] `V101SmosSecurityHardeningMigrationIntegrationTest`
- [x] Phase 61D `61D-smos-security-hardening.sh` certification script
- [ ] Run integration suite → all green on UAT
- [ ] Seed 5 initial admin users (run `SmosBootstrapAdminRunner`)
- [ ] Document RBAC permission matrix
- [ ] OpenAPI spec for `/api/auth/*` and `/api/admin/users/*`
- [ ] Audit all admin endpoints have `@PreAuthorize`

**Effort:** 20 วัน (CODE DONE in Phase 60+61)   **Owner:** 1 dev   **Status:** 🟢 code complete

---

# 3. P1 Operational Readiness

## P1.6 — Dashboard Suite

- [x] Settlement Dashboard (`/api/dashboard/settlement`)
- [x] Risk Dashboard (`/api/dashboard/risk`)
- [x] Cross-Border Dashboard (`/api/dashboard/cross-border`)
- [x] `CriticalDashboardIntegrationTest`
- [x] Phase 61E `61E-dashboard-promotion-readiness.sh`
- [ ] Transaction Dashboard (BRD 8.1)
- [ ] Participant Dashboard (BRD 8.1)
- [ ] Infrastructure Dashboard (BRD 8.1)
- [ ] DR Dashboard (BRD 8.1)
- [ ] Verify RBAC: each dashboard restricted to relevant role
- [ ] Frontend hook into SMOS Lovable portal

**Effort:** 15 วัน (3/7 done, 4 remain optional)   **Status:** 🟡 partial

---

## P1.7 — Promotion Eligibility DSL

- [x] JSON allowlist DSL evaluator
- [x] `PromotionEligibilityEvaluatorTest`
- [x] Feature flag `switching.promotion.enabled` (default off)
- [ ] Decision: enable at Go-Live? (Product team)
- [ ] Seed 3 sample promotions for UAT
- [ ] Budget cap enforcement test under concurrent load
- [ ] Funder ledger reconciliation report

**Status:** 🟢 code done

---

# 4. P2 Nice-to-Have (Deferred)

## P2.8 — WhatsApp Notification Channel
- [ ] Apply for Twilio WhatsApp Business or AWS SNS verification (2–3 week wait)
- [ ] Implement `WhatsAppDeliveryService`
- [ ] Add to `NotificationChannelRouter`

**Workaround for Go-Live:** Use Email + Dashboard alerts only

## P2.9 — One-Click DR Switchover UI
- [ ] DR controller + service
- [ ] Failover automation
- [ ] Replication lag monitor

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

**Exit:** Evidence bundle ใน `scripts/execute-and-verify/evidence/<run-id>/`

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

# 7. 🆕 Phase 61 — UAT Preflight (run before Phase 54)

Orchestrator: `scripts/phase61/run_phase61.sh`

- [x] 61A scripts: `61A-build-test-green-closure.sh`
- [x] 61B scripts: `61B-migration-data-integrity.sh`
- [x] 61C scripts: `61C-uat-deployment-contract.sh`
- [x] 61D scripts: `61D-smos-security-hardening.sh`
- [x] 61E scripts: `61E-dashboard-promotion-readiness.sh`
- [x] 61F scripts: `61F-secret-supply-chain-closure.sh`
- [x] 61G scripts: `61G-performance-capacity-certification.sh`
- [x] 61H scripts: `61H-settlement-reconciliation-scale.sh`
- [x] 61I scripts: `61I-resilience-alert-drills.sh`
- [x] 61J scripts: `61J-uat-evidence-rc-gate.sh`
- [x] Phase 61 preflight (`scripts/execute-and-verify/07-phase61-preflight.sh`)
- [x] CI workflow (`.github/workflows/phase61-certification.yml`)
- [x] 5 attestation JSON templates
- [x] `config/phase61-uat-infrastructure-contract.yaml`
- [ ] **Execute** Phase 61 against UAT environment
- [ ] Generate signed evidence manifest
- [ ] Phase 61J UAT entry attestation sign-off

**Status:** 🟢 scripts ready / execution pending

---

# 8. Phase 54 — UAT Certification

รันบน UAT cluster — `scripts/certification/run_phase54_certification.sh`

- [ ] 54A Build & Test certification
- [ ] 54B Migration certification (V1→V101)
- [ ] 54C UAT deployment rehearsal
- [ ] 54D Performance & capacity (uses P0.3 evidence)
- [ ] 54E Settlement 500k
- [ ] 54F Backup / PITR (uses P0.4 evidence)
- [ ] 54G DR & failure recovery (uses P0.4 evidence)
- [ ] 54H Security & supply chain
- [ ] 54I Observability & alert
- [ ] 54J Go-Live rehearsal + RC assembly
- [ ] Sign certification manifest

---

# 9. Phase 55 — Production Go-Live

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

# 10. Post Go-Live BAU (Hypercare 14 days)

- [ ] Day 1: 24/7 SRE coverage
- [ ] Day 1: Watch all 47 alerts firing as expected
- [ ] Day 1: Reconciliation match check (every cycle)
- [ ] Day 3: First daily settlement complete
- [ ] Day 7: First weekly recon clean
- [ ] Day 14: Hypercare exit review
- [ ] Day 14: Sign hypercare-exit acceptance
- [ ] Day 14: Hand over to standard ops

---

# 11. 🆕 Additional Scope (จาก audits)

## 11.1 — Read Replica Routing

- [ ] Add `RoutingDataSource` based on `@Transactional(readOnly=true)`
- [ ] Configure 2 HikariCP pools (primary 30 / replica 20)
- [ ] Mark dashboard queries + reports as `readOnly=true`
- [ ] Wrap with `LazyConnectionDataSourceProxy`
- [ ] Integration test `pg_is_in_recovery()` routes correctly
- [ ] Document read-your-writes consistency policy

**Effort:** 1 วัน   **Why:** ลด load primary 50–70%

## 11.2 — Standardize NUMERIC precision

- [ ] เลือก standard: `NUMERIC(24,4)` สำหรับ money
- [ ] Audit `NUMERIC(19,4)`, `NUMERIC(18,2)`, `NUMERIC(20,2)` → migration เปลี่ยน
- [ ] Document precision policy

**Effort:** 0.5 วัน

## 11.3 — Hourly Partition Strategy (เผื่อ peak TPS > 5K)

- [ ] Build `precreate_hourly_partitions()` (V102 reserved)
- [ ] Build `consolidate_hourly_to_daily()`
- [ ] Add `@Scheduled` ทุก hour (48h lookahead)
- [ ] Trigger: peak TPS > 5K from Phase 54D perf test

**Effort:** 3 วัน (ทำเมื่อ trigger)

## 11.4 — JPA N+1 Audit

- [ ] Enable `SQL_INSPECT_NPLUS1` ใน UAT
- [ ] รัน performance smoke + check log
- [ ] Add `@EntityGraph` ที่จุดร้อน
- [ ] Confirm pagination ทุก list endpoint

**Effort:** 2 วัน

## 11.5 — Distributed Tracing (OpenTelemetry)

- [ ] Add `opentelemetry-spring-boot-starter`
- [ ] Configure OTLP exporter to Jaeger/Tempo
- [ ] Propagate trace IDs across Kafka outbox
- [ ] Add trace_id to audit log records

**Effort:** 3 วัน

## 11.6 — Chaos Engineering Suite

- [ ] Install Chaos Mesh / LitmusChaos on UAT
- [ ] Define 10 scenarios
- [ ] Run weekly during hypercare

**Effort:** 5 วัน

## 11.7 — PSD2-style Consent (BRD future-scope)

- [ ] Consent record table (V103 reserved)
- [ ] OAuth2 + PKCE consent flow

**Effort:** 15 วัน (Phase IV)

## 11.8 — Rate Limiting per participant

- [ ] Implement token-bucket rate limit (Redis or in-memory)
- [ ] Configure per-participant quotas
- [ ] 429 response with `Retry-After` header

**Effort:** 2 วัน

## 11.9 — HikariCP Pool Monitoring

- [ ] Expose HikariCP metrics to Prometheus
- [ ] Alert when pool > 80% utilization
- [ ] Alert when wait time > 100ms

**Effort:** 0.5 วัน

## 11.10 — DR Documentation Drill

- [ ] Print runbooks (paper copy in SRE office)
- [ ] Off-site backup of secrets vault
- [ ] Quarterly DR drill scheduled

**Effort:** 1 วัน

---

# 📅 Timeline (Updated after Phase 61 merge)

```
Week 1 ─┬─ P0.1 mvn verify fix          (Day 1, 3 hrs)
        ├─ P0.2 secret rotation          (Day 2, SecOps)
        ├─ Read replica routing          (Day 1, +1 day) [11.1]
        ├─ NUMERIC standardise           (Day 2, +0.5 day) [11.2]
        └─ HikariCP monitoring           (Day 2, +0.5 day) [11.9]
Week 2 ─┬─ Phase 61 UAT preflight       (Day 1-2)
        ├─ P0.3 perf 10K/20K UAT         (5 days)
        ├─ P0.4 backup + DR drill        (5 days, parallel)
        ├─ JPA N+1 audit                 (2 days) [11.4]
        └─ Distributed tracing           (3 days) [11.5]
Week 3 ─┬─ Phase 54 UAT certification    (5 days)
        └─ Chaos engineering setup       (5 days, parallel) [11.6]
Week 4 ─┬─ Phase 55A-55F (RC + infra + hardening)
        └─ DR docs drill                 (1 day) [11.10]
Week 5 ─┬─ Phase 55G-55H (canary → 100%)
        └─ Phase 55I hypercare
Week 6+ Phase 55J sign-off → 🚀 GO-LIVE + 14-day hypercare
```

**Total calendar: 5–6 สัปดาห์** (เพิ่ม additional scope) หรือ **3 สัปดาห์** (ทำ P0+P1+Phase 61 อย่างเดียว)

---

# 🎯 Decision Gates

| Gate | Date | Decision Maker | เกณฑ์ |
|---|---|---|---|
| Tag RC | End of week 2 | Eng Lead | P0.1 + P0.5 ✅, Phase 61 green |
| UAT entry | Start of week 3 | QA Lead | P0.3 + P0.4 evidence ผ่าน |
| Production canary | End of week 4 | Change Manager | Phase 54 all + P0.2 done |
| 100% cutover | +1 day | Ops Lead | Reconciliation match บน canary |
| Go-Live sign-off | +14 day hypercare | Business + Ops + Security | ไม่มี P1 incident |

---

# 📞 Open Questions ที่ยังต้องตอบ

- [ ] MFA method: TOTP (already implemented) — confirm choice
- [ ] Performance SLA strict? — if P95 = 510ms ต้องเลื่อน?
- [ ] DR RPO: 5 นาที พอ หรือต้อง 0 (sync replication)?
- [ ] SMOS roles: ใช้ 8 BRD roles หรือ scheme operator เพิ่มได้?
- [ ] Promotion launch: ใช้ตั้งแต่ Go-Live หรือเปิดทีหลัง?
- [ ] Multi-region active-active หรือ active-standby?
- [ ] Final Go-Live date target?

---

# 📊 Current State (auto-generated)

```yaml
migrations_total: 96
migrations_latest: V101
reserved_gaps: [V88, V89, V90, V98, V99]
code_coverage_brd: ~98%
critical_path_code_done: 100%
critical_path_runtime_done: 0%
phase_61_scripts_ready: true
phase_61_executed: false
mvn_compile: passing
mvn_verify: pending (assertions updated, 4 test bugs remain)
last_merge_commit: f5a2453 (Phase 61A-61J UAT certification + evidence gates)
```

---

# 📈 Overall Progress

```
████████████████████████░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░  45%
```

| Tier | Progress |
|---|---|
| Tier 1: WRITE CODE | 🟢 100% (96 migrations, SMOS, dashboards, scripts) |
| Tier 2: VERIFY CODE | 🟡 60% (mvn verify pending) |
| Tier 3: PHASE 61 PREFLIGHT | 🟢 100% scripts / 🔴 0% executed |
| Tier 4: RUNTIME EVIDENCE | 🔴 0% |
| Tier 5: OPERATOR SIGN-OFF | 🔴 0% |
| Tier 6: PHASE 54 UAT CERT | 🔴 0% |
| Tier 7: PHASE 55 PROD CUTOVER | 🔴 0% |

**Distance to Go-Live:** ~3 สัปดาห์ (P0+P1+Phase 61+54+55)

---

*Single source of truth — update inline ทันทีเมื่อมีอะไรเปลี่ยน*
