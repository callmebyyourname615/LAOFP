# Go-Live Critical Path — Master Checklist

> **Last updated:** 2026-06-23 (after Phase 62+63+64 merge — commit `ef17d57`)
> **Audience:** Engineering, QA, SecOps, SRE, Change Manager
> **Purpose:** Single checklist สำหรับพา Switching ขึ้น production
> **Convention:**
> - `[x]` = done & evidence on file
> - `[~]` = code done / runtime evidence pending
> - `[ ]` = not started
> - `[-]` = N/A / deferred

---

## 🟢 Quick Snapshot

| Section | Progress | Note |
|---|---|---|
| 1. Code Foundation | 🟢 **100%** | 99 migrations (V1–V106) |
| 2. P0 Critical Fixes | 🟡 **80%** | code done + Phase 62 hardening |
| 3. P1 Operational | 🟢 **90%** | 3 dashboards + DSL + promotion budget |
| 4. P2 Nice-to-have | 🔴 **0%** | deferred |
| 5. Runtime Evidence | 🔴 **0%** | UAT drills not executed |
| 6. Operator Actions | 🔴 **0%** | secrets not rotated |
| 7. Phase 61 UAT Preflight | 🟢 **scripts ready** | not executed |
| 8. Phase 64 Entry Gate | 🟢 **scripts ready** | not executed |
| 9. Phase 54 UAT Cert | 🔴 **0%** | depends on Phase 64 handoff |
| 10. Phase 55 Production Go-Live | 🔴 **0%** | depends on Phase 54 |
| 11. Post Go-Live BAU | 🔴 **0%** | hypercare not started |
| **OVERALL** | **🟡 ~55%** | **2–3 weeks to Go-Live** |

---

# 1. Code Foundation (🟢 Complete)

- [x] Phase 1–22 core payment processing (V1–V32)
- [x] Phase 23–32 hardening (V33–V42)
- [x] Phase 33–42 governance (V43–V72)
- [x] Phase 43–52 advanced governance (V73–V82)
- [x] Phase 53A Repository Security Cleanup
- [x] Phase 53B V83 schema alignment + V84 follow-up
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
- [x] **V104 NUMERIC precision standardize** (Phase 62)
- [x] **V105 Promotion budget + funder ledger** (Phase 62)
- [x] **V106 Distributed trace correlation** (Phase 62)
- [x] OpenTelemetry OTLP exporter wired
- [x] Read replica routing implemented (Phase 62)
- [x] HikariCP pool monitoring (Phase 62)
- [x] N+1 statement inspector (Phase 62)
- [x] `./mvnw compile` passes
- [x] Dockerfile + compose syntax validate

**Migration sequence:** V1–V106 (gaps V88–V90, V98–V99 reserved) — **99 migrations**

---

# 2. P0 Critical Fixes — MUST DO before Go-Live

## P0.1 — Fix `mvn verify` test failures

- [x] Update Flyway version assertions to V106 (3 ไฟล์)
- [x] Update static verifiers to expect V106 (verify_phase53b, 53c-j, 43-52, 54a-j, 60, 61)
- [x] Phase 62 added static regression guards for 4 historical blockers
- [ ] Fix vault `ObjectMapper` missing in `WebhookEncryptionConfiguration`
- [ ] Add `provider_uid` seed in `SanctionsScreeningIntegrationTest`
- [ ] Fix FK cleanup order in `OperationsGenerateRoutesForBankIntegrationTest`
- [ ] Fix `setObject(Instant)` → `setObject(idx, instant, Types.TIMESTAMP_WITH_TIMEZONE)` in cross-border
- [ ] **Run `./mvnw verify` → 0 errors, 0 failures**
- [ ] **Run `./scripts/execute-and-verify/00-run-all.sh` → all steps green**

**Effort:** 3 ชม.   **Owner:** 1 dev   **Status:** 🟡 fixes ใส่ static guard / runtime run pending

---

## P0.2 — Secret rotation + git history purge

- [x] `security/scripts/purge-sensitive-history.sh` ready
- [x] `docs/security/SECRET_ROTATION_CHECKLIST.md` (Phase 60)
- [x] `.env.prod.example` purged of `change_me`
- [x] `config/production-environment-contract.yaml` defined
- [x] Phase 63 secret rotation runbook (`docs/phase63/security/PHASE63_SECRET_ROTATION_RUNBOOK.md`)
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
- [x] k6 sustained-2k, sustained-10k, burst-20k, soak-8h
- [x] `performance/scripts/run-k6.sh` orchestrator
- [x] Phase 61G performance certification script
- [x] Phase 64E performance evidence acquisition script
- [x] Phase 63 performance execution report template
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
- [x] Phase 61I resilience drill script
- [x] Phase 64F backup-PITR evidence + Phase 64G DR-recovery evidence scripts
- [x] Phase 63 DR execution report + backup-PITR report templates
- [ ] Full backup on UAT + verify SHA-256 + row count
- [ ] Restore drill → RPO < 5 min, RTO < 30 min
- [ ] DR pod kill / Kafka fail / network partition / S3 down / external timeout
- [ ] Region failover (ถ้า multi-region)
- [ ] Verify no transaction loss + outbox replay idempotent
- [ ] Capture all evidence in `dr/evidence/<date>/`
- [ ] Sign `DR_SIGN_OFF.md`

**Effort:** 5 วัน   **Owner:** 1 SRE   **Status:** 🟡 code + templates ready / UAT pending

---

## P0.5 — SMOS User & Access Management

- [x] V97 migration: users, roles, permissions, maker_checker_requests
- [x] V101 SMOS security hardening (Phase 61)
- [x] 8 roles seeded
- [x] `UserManagementService` CRUD
- [x] `TotpService` MFA implementation
- [x] `SmosTokenService` JWT issue + refresh + session entity
- [x] `SmosBootstrapAdminRunner`
- [x] `MakerCheckerService` 2-person approval
- [x] `SettlementApprovalActionHandler`
- [x] `SmosJwtAuthenticationFilter` (Phase 61 hardened)
- [x] `PasswordPolicyService` + test (Phase 61)
- [x] `AuthSessionEntity` + repository (Phase 61)
- [x] RBAC enforcement via Spring Security
- [x] `SmosUserManagementIntegrationTest`
- [x] `SmosSecurityCertificationIntegrationTest` (Phase 60)
- [x] `SmosSessionSecurityIntegrationTest` (Phase 61)
- [x] `V101SmosSecurityHardeningMigrationIntegrationTest`
- [x] Permission matrix + bounded API pagination (Phase 62)
- [x] OpenAPI contract for `/api/auth/*` and `/api/admin/users/*` (Phase 62)
- [ ] Run integration suite → all green on UAT
- [ ] Seed initial admin users via `SmosBootstrapAdminRunner`
- [ ] Audit all admin endpoints have `@PreAuthorize`

**Effort:** 20 วัน   **Owner:** 1 dev   **Status:** 🟢 code complete / runtime provisioning pending

---

# 3. P1 Operational Readiness

## P1.6 — Dashboard Suite

- [x] Settlement Dashboard (`/api/dashboard/settlement`)
- [x] Risk Dashboard (`/api/dashboard/risk`)
- [x] Cross-Border Dashboard (`/api/dashboard/cross-border`)
- [x] `CriticalDashboardIntegrationTest`
- [x] Phase 61E dashboard readiness script
- [x] **Replica reads + no-store + freshness controls** (Phase 62)
- [x] **RBAC hardened on dashboards** (Phase 62)
- [ ] Transaction Dashboard (BRD 8.1)
- [ ] Participant Dashboard (BRD 8.1)
- [ ] Infrastructure Dashboard (BRD 8.1)
- [ ] DR Dashboard (BRD 8.1)
- [ ] Frontend hook into SMOS Lovable portal

**Effort:** 15 วัน (3/7 done)   **Status:** 🟡 partial

---

## P1.7 — Promotion Eligibility DSL

- [x] JSON allowlist DSL evaluator
- [x] `PromotionEligibilityEvaluatorTest`
- [x] Feature flag `switching.promotion.enabled` (default off)
- [x] **V105 budget reservation + funder ledger + expiry controls** (Phase 62)
- [x] `PromotionBudgetServiceIntegrationTest` (Phase 62)
- [ ] Decision: enable at Go-Live? (Product team)
- [ ] Seed 3 sample promotions for UAT
- [ ] Budget cap enforcement test under concurrent load
- [ ] Funder ledger reconciliation report

**Status:** 🟢 code done

---

# 4. P2 Nice-to-Have (Deferred)

## P2.8 — WhatsApp Notification Channel
- [ ] Apply for Twilio WhatsApp Business or AWS SNS verification
- [ ] Implement `WhatsAppDeliveryService`
- [ ] Add to `NotificationChannelRouter`

## P2.9 — One-Click DR Switchover UI
- [ ] DR controller + service
- [ ] Failover automation
- [ ] Replication lag monitor

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

# 7. Phase 61 — UAT Preflight

Orchestrator: `scripts/phase61/run_phase61.sh`

- [x] 61A-61J scripts (10 step)
- [x] Phase 61 preflight (`scripts/execute-and-verify/07-phase61-preflight.sh`)
- [x] CI workflow + 5 attestation templates + infra contract
- [ ] Execute Phase 61 against UAT environment
- [ ] Generate signed evidence manifest
- [ ] Phase 61J UAT entry attestation sign-off

**Status:** 🟢 scripts ready / execution pending

---

# 8. 🆕 Phase 64 — Phase 54 Entry Gate (added Phase 62-64 merge)

Orchestrator: `scripts/phase64/` + preflight `scripts/execute-and-verify/08-phase64-preflight.sh`

- [x] 64A UAT environment readiness script
- [x] 64B Phase 61 evidence acquisition
- [x] 64C runtime evidence acquisition
- [x] 64D test evidence certification
- [x] 64E performance & capacity evidence
- [x] 64F backup-PITR evidence
- [x] 64G DR recovery evidence
- [x] 64H alert firing certification
- [x] 64I Phase 54 entry gate
- [x] 64J signed UAT handoff bundle
- [x] `build_signed_bundle.py` aggregator
- [x] Entry decision schema + result schema
- [x] 4 attestation templates (Alert, Backup-PITR, DR, Handoff)
- [x] CI workflow `phase64-uat-evidence.yml`
- [ ] **Execute** Phase 64 after Phase 61 evidence collected
- [ ] Build signed handoff bundle
- [ ] Phase 54 entry gate green

**Status:** 🟢 scripts ready / depends on Phase 61 execution

---

# 9. Phase 54 — UAT Certification

- [ ] 54A Build & Test certification
- [ ] 54B Migration certification (V1→V106)
- [ ] 54C UAT deployment rehearsal
- [ ] 54D Performance & capacity
- [ ] 54E Settlement 500k
- [ ] 54F Backup / PITR
- [ ] 54G DR & failure recovery
- [ ] 54H Security & supply chain
- [ ] 54I Observability & alert
- [ ] 54J Go-Live rehearsal + RC assembly
- [ ] Sign certification manifest

---

# 10. Phase 55 — Production Go-Live

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

# 11. Post Go-Live BAU (Hypercare 14 days)

- [ ] Day 1: 24/7 SRE coverage
- [ ] Day 1: Watch all 47 alerts firing as expected
- [ ] Day 1: Reconciliation match check (every cycle)
- [ ] Day 3: First daily settlement complete
- [ ] Day 7: First weekly recon clean
- [ ] Day 14: Hypercare exit review
- [ ] Day 14: Sign hypercare-exit acceptance
- [ ] Day 14: Hand over to standard ops

---

# 12. 🆕 Additional Scope

## 12.1 — Read Replica Routing
- [x] **DONE** (Phase 62) — `RoutingDataSource` + `LazyConnectionDataSourceProxy`
- [x] Dashboards mark `@Transactional(readOnly = true)`
- [ ] Document read-your-writes policy

## 12.2 — Standardize NUMERIC precision
- [x] **DONE** (V104 Phase 62)

## 12.3 — Hourly Partition (เผื่อ peak > 5K TPS)
- [ ] Trigger: peak TPS > 5K from Phase 54D perf test
- [ ] Build precreate_hourly_partitions() + consolidate_hourly_to_daily()

## 12.4 — JPA N+1 Audit
- [x] **DONE** (Phase 62 — N+1 statement inspector + test)

## 12.5 — Distributed Tracing (OpenTelemetry)
- [x] **DONE** (Phase 62 — OTLP exporter + V106 trace correlation + `TraceContextSupportTest`)

## 12.6 — Chaos Engineering Suite
- [ ] Install Chaos Mesh / LitmusChaos on UAT
- [ ] Define 10 scenarios

## 12.7 — PSD2-style Consent (BRD future-scope)
- [ ] Phase IV

## 12.8 — Rate Limiting per participant
- [ ] Token-bucket rate limit
- [ ] Per-participant quotas

## 12.9 — HikariCP Pool Monitoring
- [x] **DONE** (Phase 62)

## 12.10 — DR Documentation Drill
- [x] Phase 63 added 18 operational runbooks (alert delivery, capacity autoscaling, cert lifecycle, recon, crypto rotation, lineage, decision rules, double-entry, etc.)
- [ ] Print paper copies
- [ ] Off-site backup of secrets vault

---

# 📅 Timeline

```
Week 1 ─┬─ P0.1 mvn verify fix (3 hrs)
        ├─ P0.2 secret rotation (SecOps, 6 hrs)
        └─ Provision UAT environment
Week 2 ─┬─ Phase 61 UAT preflight execution
        ├─ P0.3 perf 10K/20K UAT (5 days)
        └─ P0.4 backup + DR drill (parallel)
Week 3 ─┬─ Phase 64 evidence acquisition + signed bundle
        └─ Phase 54 UAT certification kickoff
Week 4 ─── Phase 54 cert (5 days)
Week 5 ─┬─ Phase 55A-55F (RC + infra + hardening)
Week 6 ─┬─ Phase 55G-55H (canary → 100%) + Phase 55I hypercare
Week 7 ─── Phase 55J sign-off → 🚀 GO-LIVE + 14-day hypercare
```

**Total: 6–7 สัปดาห์** (เพิ่ม Phase 64 gate)

---

# 🎯 Decision Gates

| Gate | Date | Decision Maker | เกณฑ์ |
|---|---|---|---|
| Tag RC | End of week 2 | Eng Lead | P0.1 + P0.5 ✅, Phase 61 green |
| UAT entry | Start of week 3 | QA Lead | P0.3 + P0.4 evidence ผ่าน |
| Phase 54 entry | End of week 3 | QA Lead | Phase 64 signed bundle ✅ |
| Production canary | End of week 4 | Change Manager | Phase 54 all + P0.2 done |
| 100% cutover | +1 day | Ops Lead | Reconciliation match บน canary |
| Go-Live sign-off | +14 day hypercare | Business + Ops + Security | ไม่มี P1 incident |

---

# 📞 Open Questions

- [ ] MFA method: TOTP (already implemented) — confirm
- [ ] Performance SLA strict? — if P95 = 510ms ต้องเลื่อน?
- [ ] DR RPO: 5 นาที พอ หรือต้อง 0?
- [ ] SMOS roles: 8 BRD roles หรือ scheme operator เพิ่มได้?
- [ ] Promotion launch: ใช้ตั้งแต่ Go-Live หรือเปิดทีหลัง?
- [ ] Multi-region active-active หรือ active-standby?
- [ ] Final Go-Live date target?

---

# 📊 Current State

```yaml
migrations_total: 99
migrations_latest: V106
reserved_gaps: [V88, V89, V90, V98, V99]
code_coverage_brd: ~98%
critical_path_code_done: 100%
critical_path_runtime_done: 0%
phase_61_scripts_ready: true
phase_64_scripts_ready: true
phase_61_executed: false
phase_64_executed: false
mvn_compile: passing
mvn_verify: pending
last_merge_commit: ef17d57 (Phase 62 + 63 + 64)
```

---

# 📈 Overall Progress

```
████████████████████████████████░░░░░░░░░░░░░░░░░░░░░░░░  55%
```

| Tier | Progress |
|---|---|
| Tier 1: WRITE CODE | 🟢 100% (99 migrations, SMOS, dashboards, tracing, replica routing) |
| Tier 2: VERIFY CODE | 🟡 70% |
| Tier 3: PHASE 61 PREFLIGHT | 🟢 100% scripts / 🔴 0% executed |
| Tier 4: PHASE 64 ENTRY GATE | 🟢 100% scripts / 🔴 0% executed |
| Tier 5: RUNTIME EVIDENCE | 🔴 0% |
| Tier 6: OPERATOR SIGN-OFF | 🔴 0% |
| Tier 7: PHASE 54 UAT CERT | 🔴 0% |
| Tier 8: PHASE 55 PROD CUTOVER | 🔴 0% |

**Distance to Go-Live:** ~3 สัปดาห์ (P0+Phase 61+64+54+55)

---

*Single source of truth — update inline ทันทีเมื่อมีอะไรเปลี่ยน*
