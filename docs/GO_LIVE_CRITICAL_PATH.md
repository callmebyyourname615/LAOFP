# Go-Live Critical Path — Master Checklist

> **Last updated:** 2026-06-23 (Phase 74-77 merged — commit `ba1f5a0`)
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
| 2. P0 Critical Fixes | 🟡 **95%** | 4/5 test bugs fixed (Phase 65/69) |
| 3. P1 Operational | 🟢 **90%** | 3 dashboards + DSL + promotion budget |
| 4. P2 Nice-to-have | 🔴 **0%** | deferred |
| 5. Runtime Evidence | 🔴 **0%** | UAT drills not executed |
| 6. Operator Actions | 🔴 **0%** | secrets not rotated |
| 7. Phase 61 UAT Preflight | 🟢 **scripts ready** | not executed |
| 8. Phase 64 Entry Gate | 🟢 **scripts ready** | not executed |
| 9. 🆕 Phase 65 Phase-54 Handoff | 🟢 **scripts ready** | not executed |
| 10. 🆕 Phase 66 UAT Runtime Closure | 🟢 **scripts ready** | not executed |
| 11. Phase 54 UAT Cert | 🔴 **0%** | gated by Phase 65/66 |
| 12. 🆕 Phase 67 Production Cutover | 🟢 **scripts ready** | not executed |
| 13. Phase 55 Production Go-Live | 🔴 **0%** | depends on Phase 67 |
| 14. Post Go-Live BAU | 🔴 **0%** | hypercare not started |
| 🆕 Phase 68 UAT Activation | 🟢 **scripts ready** | not executed |
| 🆕 Phase 69 Verification Closure | 🟢 **scripts ready** + test fixes | not executed |
| 🆕 Phase 70 Traffic & Financial Safety | 🟢 **code wired** | rate-limit + read-your-writes |
| 🆕 Phase 71 UAT Certification Closure | 🟢 **scripts ready** + temporal JDBC fix | not executed |
| 🆕 Phase 72 Final UAT Closure | 🟢 **scripts ready** | not executed |
| 🆕 Phase 73 Chaos Certification | 🟢 **tooling ready** | real UAT chaos drills not executed |
| 🆕 Phase 74 UAT Runtime Certification | 🟢 **scripts + 7 attestations** | not executed |
| 🆕 Phase 75 Phase 54 Production Handoff | 🟢 **policy ready** | not executed |
| 🆕 Phase 76 Operational Evidence Aggregation | 🟢 **7 policies** | not executed |
| 🆕 Phase 77 Continuous Assurance (BAU) | 🟢 **11 policies** | not executed |
| **OVERALL** | **🟡 ~63%** | **2 weeks to Go-Live** |

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
- [x] Fix vault `ObjectMapper` missing in `WebhookEncryptionConfiguration` (Phase 69)
- [x] Add `provider_uid` seed in `SanctionsScreeningIntegrationTest` (Phase 65A)
- [x] Fix FK cleanup order in `OperationsGenerateRoutesForBankIntegrationTest` (Phase 69)
- [x] Fix cross-border JDBC temporal binding with `JdbcTemporalBinder` (Phase 71)
- [x] Fix Instant → TIMESTAMPTZ binding in `PostgresTemporalBinder` (P0.1 bug e — closes the final mvn-verify root cause). See [docs/BUG_E_INSTANT_SQL_TYPE_CHECKLIST.md](BUG_E_INSTANT_SQL_TYPE_CHECKLIST.md).
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

# 9. 🆕 Phase 65 — Phase 54 Handoff

Orchestrator: `scripts/phase65/` + preflight `scripts/execute-and-verify/09-phase65-preflight.sh`

- [x] 65A Maven test blocker closure (fixes `provider_uid` in SanctionsScreeningIntegrationTest)
- [x] 65B Full build + migration certification scripts
- [x] 65C SMOS runtime provisioning + security audit scripts
- [x] 65D Secret rotation + git history purge scripts (`generate_phase65_rotated_secrets.sh`)
- [x] 65E–65J UAT infrastructure attestation + Phase 54 handoff bundle
- [x] CI workflow `phase65-certification.yml`
- [x] 6 attestation templates (DECISIONS, PHASE54_HANDOFF, SECRET_ROTATION, SMOS, SMOS_PROVISIONING, UAT_INFRA)
- [x] `config/phase65-uat-infrastructure-contract.yaml`
- [ ] **Execute** Phase 65 against UAT
- [ ] Build Phase 54 handoff bundle
- [ ] Phase 54 handoff sign-off

**Status:** 🟢 scripts ready / execution pending

---

# 10. 🆕 Phase 66 — UAT Runtime Closure (10 step)

Orchestrator: `scripts/phase66/` + verifier `scripts/verify_phase66_static.py`

- [x] 66A Phase 65 handoff + collision guard
- [x] 66B UAT dependency matrix
- [x] 66C Build & test closure
- [x] 66D Migration & data integrity certification (`sql/phase66/data-integrity-checks.sql`)
- [x] 66E Performance & capacity certification
- [x] 66F Backup & PITR certification
- [x] 66G DR runtime certification
- [x] 66H Secret rotation ceremony
- [x] 66I SMOS runtime security certification
- [x] 66J Runtime closure & Phase 54 decision
- [x] 5 JSON schemas + 5 YAML configs
- [x] CI workflow `phase66-runtime-closure.yml`
- [ ] **Execute** Phase 66 against UAT
- [ ] Build runtime closure manifest
- [ ] Phase 54 decision (GO / NO-GO)

**Status:** 🟢 scripts ready / execution pending

---

# 11. Phase 54 — UAT Certification

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

# 12. 🆕 Phase 67 — Production Cutover (10 step)

Orchestrator: `scripts/phase67/`

- [x] 67A Release identity freeze gate
- [x] 67B Production infrastructure gate
- [x] 67C Immutable RC provenance
- [x] 67D Financial cutover baseline
- [x] 67E Canary health gate
- [x] 67F Progressive traffic gate
- [x] 67G–67J Hypercare + BAU acceptance manifests
- [x] 4 JSON schemas (cutover-decision, command-center-event, bau-acceptance, result)
- [x] 5 attestation templates (CHANGE_FREEZE, COMMAND_CENTER, HEALTHY_CUTOVER, HYPERCARE_14_DAY, ROLLBACK)
- [x] CI workflow `phase67-production-cutover.yml`
- [x] `config/phase67-production-cutover-policy.yaml`
- [x] Operator runbook `docs/phase67/PHASE67_OPERATOR_RUNBOOK.md`
- [x] Exit criteria `docs/phase67/PHASE67_EXIT_CRITERIA.md`
- [ ] **Execute** Phase 67 in production
- [ ] Sign cutover decision
- [ ] Complete 14-day hypercare

**Status:** 🟢 scripts ready / execution pending

---

# 13. Phase 55 — Production Go-Live

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

# 14. 🆕 Phase 68 — UAT Activation + Phase 54 Kickoff

Orchestrator: `scripts/phase68/` + preflight `scripts/execute-and-verify/10-phase68-preflight.sh`

- [x] Performance policy config (`config/phase68-performance-policy.yaml`)
- [x] Resilience policy config (`config/phase68-resilience-policy.yaml`)
- [x] UAT activation config (`config/phase68-uat-activation.yaml`)
- [x] 6 attestation templates (Performance, Phase54 Kickoff, Resilience, Secret Rotation, SMOS Runtime, UAT)
- [x] Phase 68 result schema
- [x] CI workflow `phase68-certification.yml`
- [x] Implementation report + delivery notes
- [ ] **Execute** Phase 68 against UAT
- [ ] Phase 54 kickoff sign-off

**Status:** 🟢 scripts ready / execution pending

---

# 15. 🆕 Phase 69 — Verification Closure

Orchestrator: `scripts/phase69/` (validation evidence bundled)

- [x] Fix vault ObjectMapper bug in `WebhookEncryptionConfiguration` (P0.1 bug #2)
- [x] Fix FK cleanup in `OperationsGenerateRoutesForBankIntegrationTest` (P0.1 bug #4)
- [x] `WebhookEncryptionConfigurationContextTest` + `WebhookEncryptionConfigurationTest`
- [x] Phase 69 verification policy config
- [x] Release verification attestation template
- [x] Validation evidence bundle (preflight/local/full-guard logs + SHA256SUMS)
- [x] Phase 69 exit criteria + operator runbook
- [x] CI workflow `phase69-verification-closure.yml`
- [ ] **Execute** Phase 69 verification gate
- [ ] Sign release verification attestation

**Status:** 🟢 code fixes done / verification gate pending

---

# 16. 🆕 Phase 70 — Traffic & Financial Safety

Orchestrator: `scripts/phase70/run_phase70.sh` + static `verify_phase70_static.py`

- [x] Participant rate limit policy (`config/phase70-participant-traffic-policy.yaml`)
- [x] `RateLimitFilter` token-bucket enhancements
- [x] `ParticipantTokenBucketServiceTest`
- [x] Read-your-writes consistency policy doc
- [x] `ConsistencyAwareReportingJdbcOperations` (replica routing wired)
- [x] `application.yml` + `-prod.yml` + `-staging.yml` rate-limit config
- [x] `RailMessageJournalService` + `OperationsGenerateRoutesForBankService` enhancements
- [x] `PromotionBudgetConcurrencyIntegrationTest` (concurrent budget cap test)
- [x] Phase 70 result schema
- [x] CI workflow `phase70-traffic-financial-safety.yml`
- [x] Operator runbook + overview + validation report
- [ ] **Execute** Phase 70 verification
- [ ] Tune rate-limit thresholds per participant

**Status:** 🟢 code wired / verification pending

---

# 17. 🆕 Phase 71 — UAT Certification Closure

Orchestrator: `scripts/phase71/run_phase71.sh` + preflight `scripts/execute-and-verify/11-phase71-preflight.sh`

- [x] 71A typed JDBC temporal binding and cross-border regression test
- [x] 71B–71J UAT certification closure scripts, schemas, policies and attestation templates
- [x] CI workflow `phase71-uat-certification.yml`
- [x] Phase 71 operator runbook and exit criteria
- [ ] **Execute** Phase 71 against UAT with the authoritative migration chain
- [ ] Complete Maven verification and collect non-synthetic runtime evidence
- [ ] Sign Phase 54 entry bundle

**Status:** 🟢 tooling merged / execution blocked pending UAT prerequisites

---

# 18. 🆕 Phase 72 — Final UAT Closure

Orchestrator: `scripts/phase72/run_phase72.sh` + preflight `scripts/execute-and-verify/13-phase72-final-uat-closure.sh`

- [x] 72A–72J final UAT closure scripts, schemas, policies and attestation templates
- [x] Cross-border temporal-binding static verifier and regression test
- [x] CI workflow `phase72-final-uat-closure.yml`
- [x] Post-restore financial-integrity SQL check
- [ ] **Execute** Phase 72 after Phase 71 has a complete UAT handoff
- [ ] Produce commit-matched, non-synthetic final GO attestation

**Status:** 🟢 tooling merged / final decision pending runtime evidence

---

# 19. 🆕 Phase 73 — Chaos Certification

Orchestrator: `scripts/phase73/run_phase73.sh`

- [x] 73A–73J chaos certification scripts, schemas, policy and operator runbook
- [x] Eight Chaos Mesh experiment manifests (pod, database, Kafka, object storage, API, DNS, CPU and memory)
- [x] CI workflow `phase73-chaos-certification.yml`
- [x] Synthetic orchestration/evidence validation fixtures
- [ ] Install or confirm Chaos Mesh in UAT
- [ ] Configure real UAT dependency CIDRs and read-only financial-integrity adapter
- [ ] Execute and sign all eight real UAT experiments

**Status:** 🟢 tooling merged / synthetic evidence is not UAT certification

---

# 20. 🆕 Phase 74 — UAT Runtime Certification

Orchestrator: `scripts/phase74/`

- [x] Performance policy config (`config/phase74-performance-policy.yaml`)
- [x] Resilience policy config (`config/phase74-resilience-policy.yaml`)
- [x] UAT runtime certification config (`config/phase74-uat-runtime-certification.yaml`)
- [x] 7 attestation templates: Capacity, Performance Baseline, Phase 54 Entry, Resilience Chaos, Secret Rotation, Settlement, SMOS Runtime
- [x] Phase 74 result schema
- [x] CI workflow `phase74-uat-runtime-certification.yml`
- [x] Operator runbook + exit criteria + overview docs
- [ ] Execute Phase 74 against UAT
- [ ] Phase 54 entry attestation sign-off

**Status:** 🟢 scripts ready / execution pending

---

# 21. 🆕 Phase 75 — Phase 54 Production Handoff

Orchestrator: `scripts/phase75/`

- [x] Production handoff policy (`config/phase75-production-handoff-policy.yaml`)
- [x] Phase 75 exit criteria + operator runbook + overview
- [x] CI workflow `phase75-phase54-production-handoff.yml`
- [x] Phase 75 result schema + manifest schema
- [ ] Execute Phase 75
- [ ] Sign production handoff

**Status:** 🟢 scripts ready / execution pending

---

# 22. 🆕 Phase 76 — Operational Evidence Aggregation

Orchestrator: `scripts/phase76/`

- [x] 7 governance policies:
  - approval-policy / decision-policy / coordinator-plan
  - evidence-source-registry / file-ownership-policy
  - waiver-policy / application-readiness.example
- [x] Approval & waiver policy doc
- [x] Evidence integrity policy doc
- [x] Phase 74/75 handoff baseline doc
- [x] CI workflow `phase76-operational-evidence.yml`
- [x] Phase 76 schemas (decision, manifest, ledger entry)
- [ ] Execute Phase 76 evidence aggregation
- [ ] Sign evidence ledger

**Status:** 🟢 policies ready / execution pending

---

# 23. 🆕 Phase 77 — Continuous Assurance Framework (BAU)

Orchestrator: `scripts/phase77/`

- [x] 11 BAU policies:
  - application-continuous-assurance.example
  - backup-dr-policy / bau-ownership
  - capacity-policy / compliance-export-policy
  - hypercare-policy / key-lifecycle-policy
  - participant-operations-policy / reconciliation-policy
  - scorecard-policy / slo-policy
- [x] CI workflow `phase77-continuous-assurance.yml`
- [x] Phase 77 schemas + scorecard contract
- [x] BAU ownership RACI matrix
- [ ] Activate continuous assurance jobs post Go-Live
- [ ] Quarterly recertification

**Status:** 🟢 policies ready / activated post Go-Live

---

# 24. Post Go-Live BAU (Hypercare 14 days)

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
- [x] Document read-your-writes policy (Phase 70 — `docs/phase70/READ_YOUR_WRITES_CONSISTENCY_POLICY.md`)

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
- [x] Phase 73 chaos certification tooling and 8 scenario manifests merged
- [ ] Install Chaos Mesh / LitmusChaos on UAT
- [ ] Execute and certify the 8 UAT scenarios

## 12.7 — PSD2-style Consent (BRD future-scope)
- [ ] Phase IV

## 12.8 — Rate Limiting per participant
- [x] Token-bucket rate limit (Phase 70 — `RateLimitFilter` + `ParticipantTokenBucketService`)
- [x] Per-participant quotas (`config/phase70-participant-traffic-policy.yaml`)
- [ ] Tune thresholds per real-world participant traffic

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
last_merge_commit: e14be44 (Phase 68 + 69 + 70)
phase_65_scripts_ready: true
phase_66_scripts_ready: true
phase_67_scripts_ready: true
phase_68_scripts_ready: true
phase_69_test_fixes_done: true
phase_70_code_wired: true
p0_1_test_bugs_closed: 4_of_5
```

---

# 📈 Overall Progress

```
██████████████████████████████████████░░░░░░░░░░░░░░░░░░  63%
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
