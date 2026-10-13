# Phase checklist — Core Switching PROD readiness

วันที่จัดทำ: 2026-10-13 | สถานะ: เตรียมก่อน execution audit

มี 61 รายการตรวจใน 11 audit phases; หมายเลข P00–P10 เป็น phase ของการตรวจรอบนี้ ไม่ใช่เลข implementation phase เดิมของ repository.

ทุก checkbox ยังว่างเพราะยังไม่ได้ตรวจ acceptance gate ใหม่ในรอบนี้ การพบ code/test/script ไม่ใช่ PASS. ผล QA เดิมถูกล้างแล้ว; สถานะปัจจุบันคือ NOT_RUN.

ใช้ CHECKLIST.csv เป็นสถานะหลัก: NOT_RUN / PASS / FAIL / BLOCKED / NOT_APPLICABLE. PASS ต้องมี evidence path, release commit, image digest, environment, เวลา, reviewer. NOT_APPLICABLE ต้องมี scope decision และ owner sign-off; ห้ามใช้เพื่อข้าม core/security/financial gates.

Owner ในตารางเป็นบทบาทที่แนะนำ ต้องระบุชื่อผู้รับผิดชอบก่อน execution. P10 เป็นงานหลัง signed GO แต่ runbook/threshold/on-call ต้องเตรียมผ่าน P09 ก่อนเปิด traffic.

## ลำดับ phase

| Phase | ขอบเขต | Dependencies | Exit gate | อ้างอิง implementation เดิม |
|---|---|---|---|---|
| P00 | Baseline & evidence identity | ไม่มี | Freeze commit + tracked changes + image digest และ evidence index | Repo 53A, 60A, 67A/C, 69, 94–99 |
| P01 | Build, tests & migration | P00 | Fresh verify + migration clean-install/upgrade ผ่านบน isolated DB | Repo 53B, 54A/B, 61B, 65B, 68B, 71C, 74B, 78C |
| P02 | Core switching flow & financial safety | P01 | E2E success/failure/duplicate/concurrency พิสูจน์ไม่สูญหายและไม่ลงบัญชีซ้ำ | Repo 1–22, 62, 70; Phase II orchestrator |
| P03 | Participant integration & enabled features | P01, P02 | ทุก participant/rail/feature ที่เปิดใช้มี signed acceptance | Phase II 01–24; Repo 94–97 |
| P04 | Security & production configuration | P00, P01 | Secrets, auth, RBAC, TLS, mock/demo guards และ supply chain ผ่าน | Repo 53A/C–J, 60D/F, 61D, 65C/D, 66H/I |
| P05 | Settlement, reconciliation & operations | P02, P03, P04 | ยอดรวมตรงกัน, cutoff/finality/dispute/report ตรวจสอบย้อนหลังได้ | Repo 54E, 62, 70, 95/96 |
| P06 | Performance & capacity | P02–P05 บน UAT ที่แทน PROD ได้ | ผ่าน load/burst/soak/settlement benchmark ตาม policy และ workload จริง | Repo 54D/E, 61G, 64E, 66E, 68, 74, 78 |
| P07 | Recovery, DR & observability | P04, P05; ทำขนาน P06 ได้ | Restore/PITR/DR/failover และ alert delivery ผ่านพร้อมหลักฐาน | Repo 54F/G/I, 64F/G/H, 66F/G, 73, 76 |
| P08 | Authoritative UAT & runtime closure | P01–P07 | Fresh environment-bound evidence ครบ, ไม่มี critical skip/block, signed GO candidate | Repo 54, 64–66, 71/72, 74/75, 78/80, 98/99 |
| P09 | Production cutover readiness & Go/No-Go | P08 | Immutable RC, signed approvals, cutover baseline และ rollback rehearsal ผ่าน | Repo 55, 67, 79 |
| P10 | Go-live execution, hypercare & BAU | P09 signed GO ก่อนเปิด traffic | Canary 5→25→50→100 พร้อม financial checks; hypercare 14 วันและ BAU handover | Repo 55, 67, 77/79/81 |

## P00 — Baseline & evidence identity

Owner: Engineering / Release | Dependencies: ไม่มี

Exit gate: Freeze commit + tracked changes + image digest และ evidence index

- [ ] **P00-01 — Release identity** (P0, NOT_RUN)
  - ตรวจ: บันทึก HEAD, diff, untracked files; เลือก RC ที่รวมทุก intended change
  - Trace: `Report/QA_BASELINE.json; fresh release manifest`
  - ผ่านเมื่อ: Frozen source manifest และ artifact digest ตรงกับ deployment
- [ ] **P00-02 — Requirements & launch scope** (P0, NOT_RUN)
  - ตรวจ: Map BRD/API/participant/rail/features ที่ต้องเปิดวันแรก รวม legacy endpoints
  - Trace: `API_ENDPOINTS.txt; MISSING_ENDPOINTS_SPEC.md; docs/legacy/; PHASE_II_05_24_PLAN.md`
  - ผ่านเมื่อ: ทุก launch requirement มี owner, source, test และ acceptance evidence
- [ ] **P00-03 — Evidence integrity & freshness** (P0, NOT_RUN)
  - ตรวจ: ผูก evidence กับ commit/image/schema/environment/time; verify checksum และ sign-off
  - Trace: `runtime-evidence/; evidence/; schemas/`
  - ผ่านเมื่อ: ไม่มี stale/template evidence ถูกนับ PASS และทุก gate มี provenance
- [ ] **P00-04 — Verifier coverage** (P0, NOT_RUN)
  - ตรวจ: Trace CI, verify_all_static และ run-all ว่าครอบคลุม phase ใหม่จริง
  - Trace: `scripts/verify_all_static.py; scripts/execute-and-verify/00-run-all.sh; .github/workflows/`
  - ผ่านเมื่อ: Coverage matrix ครอบคลุม launch scope รวม Phase 98/99; exit 0 ไม่แทน evidence completeness

## P01 — Build, tests & migration

Owner: Engineering / QA / DBA | Dependencies: P00

Exit gate: Fresh verify + migration clean-install/upgrade ผ่านบน isolated DB

- [ ] **P01-01 — Reproducible build** (P0, NOT_RUN)
  - ตรวจ: ตรวจ Java 21, wrapper, dependency resolution และ build metadata
  - Trace: `pom.xml; Dockerfile; .github/workflows/build.yml`
  - ผ่านเมื่อ: Build สำเร็จจาก frozen source และระบุ commit/image จริง
- [ ] **P01-02 — Static contracts** (P0, NOT_RUN)
  - ตรวจ: Review verifier side effects แล้วรัน static gates และ regression tests ที่เกี่ยวข้อง
  - Trace: `scripts/verify_all_static.py; scripts/tests/; scripts/evidence/`
  - ผ่านเมื่อ: ทุก required verifier ผ่านและไม่ตกหล่น phase ใหม่
- [ ] **P01-03 — Full Maven verify** (P0, NOT_RUN)
  - ตรวจ: Review AbstractIntegrationTest ก่อนรันบน isolated disposable DB/Docker
  - Trace: `src/test/java/com/example/switching/AbstractIntegrationTest.java; target/surefire-reports/`
  - ผ่านเมื่อ: Fresh ./mvnw verify ผ่าน: failures/errors=0 และ required integration tests ไม่ถูก skip
- [ ] **P01-04 — Migration inventory drift** (P0, NOT_RUN)
  - ตรวจ: ตรวจ V118/111 เทียบ docs, verifiers, test assertions และ packaged resources
  - Trace: `src/main/resources/db/migration/; scripts/phase60/; scripts/phase65/; docs/GO_LIVE_CRITICAL_PATH.md`
  - ผ่านเมื่อ: Inventory กับ release artifact ตรงกัน; version gaps มีเหตุผลและ checksums ถูกต้อง
- [ ] **P01-05 — Clean install & upgrade** (P0, NOT_RUN)
  - ตรวจ: รัน Flyway validate/clean-install บน disposable DB และ upgrade จาก deployed schema copy
  - Trace: `src/test/java/com/example/switching/migration/; scripts/phase74/`
  - ผ่านเมื่อ: ทั้ง fresh install และ upgrade ผ่าน; ไม่มี checksum mismatch หรือ missing migration
- [ ] **P01-06 — Data safety** (P0, NOT_RUN)
  - ตรวจ: ตรวจ constraints, NUMERIC precision, indexes, migration locking และ forward recovery
  - Trace: `V104__*.sql; V117__*.sql; V118__*.sql; docs/DB_HARDENING_CHECKLIST.md`
  - ผ่านเมื่อ: พิสูจน์ไม่ลบ live data ที่ต้องเก็บ; downtime/lock budget ผ่านและ recovery tested

## P02 — Core switching flow & financial safety

Owner: Core Engineering / QA | Dependencies: P01

Exit gate: E2E success/failure/duplicate/concurrency พิสูจน์ไม่สูญหายและไม่ลงบัญชีซ้ำ

- [ ] **P02-01 — Inquiry & beneficiary** (P0, NOT_RUN)
  - ตรวจ: Trace account lookup, inquiry expiry, beneficiary token และ participant ownership
  - Trace: `inquiry/service/; transfer/service/CreateTransferService.java; iso/inquiry/`
  - ผ่านเมื่อ: valid/invalid/expired/reused/foreign inquiry และ token ให้ผลถูกต้อง
- [ ] **P02-02 — Routing & feature branches** (P0, NOT_RUN)
  - ตรวจ: ทดสอบ legacy กับ orchestrator enabled/disabled และ missing idempotency key
  - Trace: `transfer/service/TransferSubmissionService.java; routing/service/; phaseii/PushPaymentOrchestratorIntegrationTest.java`
  - ผ่านเมื่อ: ทุก branch ใน launch config ใช้ policy และ validation ที่กำหนด
- [ ] **P02-03 — Idempotency under concurrency** (P0, NOT_RUN)
  - ตรวจ: ตรวจ duplicate same payload, changed payload, inquiry reuse และ parallel submission
  - Trace: `transfer/IdempotencyIntegrationTest.java; iso/inquiry/IsoInquiryConcurrentIdempotencyIntegrationTest.java`
  - ผ่านเมื่อ: ไม่มี transfer/hold/outbox/posting ซ้ำ; payload conflict ถูกปฏิเสธ
- [ ] **P02-04 — Atomic transfer & outbox** (P0, NOT_RUN)
  - ตรวจ: Trace transaction boundary ระหว่าง hold, transfer, idempotency, ISO และ enqueue
  - Trace: `transfer/service/CreateTransferService.java; outbox/service/OutboxTransactionService.java`
  - ผ่านเมื่อ: Rollback/crash ที่แต่ละ boundary ไม่สร้าง orphan hold หรือ lost dispatch
- [ ] **P02-05 — Dispatch & replay** (P0, NOT_RUN)
  - ตรวจ: ทดสอบ worker claim, multi-instance, Kafka redelivery, shutdown/recovery และ DLQ
  - Trace: `outbox/service/OutboxProcessorService.java; outbox/queue/; outbox/OutboxConcurrentDispatchIntegrationTest.java`
  - ผ่านเมื่อ: หลัง restart/replay ไม่มี lost event หรือ duplicate bank effect; backlog มีขอบเขต
- [ ] **P02-06 — Ambiguous destination outcome** (P0, NOT_RUN)
  - ตรวจ: ทดสอบ timeout-after-accept, pacs002/camt006 mismatch, retry และ status enquiry
  - Trace: `outbox/FpreAmbiguousCheckIntegrationTest.java; outbox/FpreRetryScheduleIntegrationTest.java`
  - ผ่านเมื่อ: ผล unknown ไม่ถูกนับ final success; retry/reversal/provisional/DRS สอดคล้อง policy
- [ ] **P02-07 — Failure & compensation** (P0, NOT_RUN)
  - ตรวจ: ทดสอบ technical/business reject, auto reversal และ manual recovery
  - Trace: `outbox/FpreAutoReversalIntegrationTest.java; outbox/service/OutboxRecoveryService.java`
  - ผ่านเมื่อ: Release/consume hold ถูกครั้งเดียว; terminal transitions และ audit ถูกต้อง
- [ ] **P02-08 — AML, fraud & limits** (P0, NOT_RUN)
  - ตรวจ: ตรวจ sanctions freshness, unavailable provider, velocity, amount/currency และ rate-limit
  - Trace: `aml/; fraud/; traffic/; transfer/service/CreateTransferService.java`
  - ผ่านเมื่อ: deny/allow/fail-closed ตาม policy และ bypass ไม่สำเร็จ

## P03 — Participant integration & enabled features

Owner: Integration / QA / Product | Dependencies: P01, P02

Exit gate: ทุก participant/rail/feature ที่เปิดใช้มี signed acceptance

- [ ] **P03-01 — Real bank connectors** (P0, NOT_RUN)
  - ตรวจ: ตรวจ HTTP/MQ configuration, trust/auth, timeout และ network reachability ต่อ participant
  - Trace: `connector/registry/ConnectorRegistry.java; connector/GenericHttpConnector.java; connector/GenericMqConnector.java`
  - ผ่านเมื่อ: ทุก enabled route ชี้ real connector และผ่าน bilateral acceptance
- [ ] **P03-02 — ISO contract & callback** (P0, NOT_RUN)
  - ตรวจ: ทดสอบ pacs008/pacs002/camt005/camt006 schema, correlation และ duplicate callbacks
  - Trace: `schemas/; iso/; outbox/service/OutboxIsoMessageDispatchService.java`
  - ผ่านเมื่อ: message security และ correlation ผ่านกับ participant จริงบน UAT
- [ ] **P03-03 — Feature flag launch matrix** (P0, NOT_RUN)
  - ตรวจ: ระบุ RTP/promotion/push/cross-border/report delivery ที่เปิดและ dependencies
  - Trace: `PHASE_II_05_24_PLAN.md; application.yml; docs/runbooks/PHASE_II_*`
  - ผ่านเมื่อ: enabled feature ผ่าน E2E; deferred feature ปิดและเข้าใช้งานไม่ได้ตาม policy
- [ ] **P03-04 — Phase II money flows** (P0, NOT_RUN)
  - ตรวจ: ทดสอบ RTP auth/replay, promotion concurrent budgets, journals และ report delivery
  - Trace: `src/test/java/com/example/switching/phaseii/; promotion/; crossborder/`
  - ผ่านเมื่อ: ทุก enabled feature มี financial reconciliation และ rollback/recovery evidence
- [ ] **P03-05 — Legacy settlement & Finlink** (P0, NOT_RUN)
  - ตรวจ: ตรวจ compatibility API, treasury/tax semantics และ source balances
  - Trace: `docs/legacy/PHASE_95_LEGACY_SETTLEMENT_FILE_COMPATIBILITY.md; docs/legacy/PHASE_96_FINLINK_TREASURY_TAX_FLOWS.md`
  - ผ่านเมื่อ: scope ที่เปิดใช้ผ่าน real-data acceptance; formatting tests อย่างเดียวไม่พอ
- [ ] **P03-06 — Legacy cross-border rail facade** (P0, NOT_RUN)
  - ตรวจ: ตรวจ ITMX/PROMPTPAY, NAPAS, UPI, LFX reports และ audit events
  - Trace: `docs/legacy/PHASE_97_CROSS_BORDER_RAIL_REPORT_FILE_FACADE.md; scripts/phase98/`
  - ผ่านเมื่อ: protected endpoints ผ่าน, totals/source mapping ถูก และ export audit ครบ

## P04 — Security & production configuration

Owner: SecOps / Engineering / SRE | Dependencies: P00, P01

Exit gate: Secrets, auth, RBAC, TLS, mock/demo guards และ supply chain ผ่าน

- [ ] **P04-01 — Production startup validation** (P0, NOT_RUN)
  - ตรวจ: ตรวจ prod profile, config contract, scheduler/outbox switches และ negative startup cases
  - Trace: `config/ProductionStartupValidator.java; src/main/resources/application.yml; config/production-environment-contract.yaml`
  - ผ่านเมื่อ: Invalid critical config ทำให้ startup fail; valid prod config boot ได้
- [ ] **P04-02 — Secret rotation & custody** (P0, NOT_RUN)
  - ตรวจ: ตรวจ rotation evidence, Vault/External Secrets, expiry, access และ redaction
  - Trace: `docs/security/SECRET_ROTATION_CHECKLIST.md; docs/security/VAULT_EXTERNAL_SECRETS_RUNBOOK.md`
  - ผ่านเมื่อ: credentials ที่เคยเปิดเผยถูก revoke และมี evidence โดยไม่บันทึก secret ลงรายงาน
- [ ] **P04-03 — Repository secret exposure** (P0, NOT_RUN)
  - ตรวจ: ตรวจ current tree/history/artifact/log แบบ redacted; reconcile purge status
  - Trace: `security/; .gitleaks.toml; .github/workflows/security.yml`
  - ผ่านเมื่อ: ไม่มี active leaked secret; history remediation มี signed evidence ถ้าจำเป็น
- [ ] **P04-04 — Auth & authorization** (P0, NOT_RUN)
  - ตรวจ: ทดสอบ OAuth/SMOS token expiry, revocation, issuer/audience, RBAC และ tenant isolation
  - Trace: `security/; usermgmt/; security/SecurityAuthorizationIntegrationTest.java`
  - ผ่านเมื่อ: positive/negative endpoint authorization matrix ครบ launch API
- [ ] **P04-05 — Admin controls** (P0, NOT_RUN)
  - ตรวจ: ตรวจ MFA, bootstrap admin, sessions และ maker-checker separation
  - Trace: `src/test/java/com/example/switching/usermgmt/service/; usermgmt/`
  - ผ่านเมื่อ: ไม่มี default/admin bypass; self-approval และ unauthorized settlement ถูกปฏิเสธ
- [ ] **P04-06 — Transport & egress** (P0, NOT_RUN)
  - ตรวจ: ตรวจ TLS/mTLS ingress trust, header spoofing, network policies และ webhook SSRF
  - Trace: `nginx/; k8s/; webhook/security/; security/config/SecurityConfig.java`
  - ผ่านเมื่อ: trust boundaries และ allowlist ผ่าน negative tests; cert lifecycle มี owner
- [ ] **P04-07 — Demo & mock isolation** (P0, NOT_RUN)
  - ตรวจ: ตรวจ V117 cleanup, active MOCK routes, test fixtures และ demo endpoints ใน prod
  - Trace: `db/migration/V117__remove_demo_seed_data.sql; config/ProductionStartupValidator.java`
  - ผ่านเมื่อ: live config/DB ไม่มี mock bank/biller routes หรือ demo keys ที่ใช้งานได้
- [ ] **P04-08 — Supply chain & audit privacy** (P0, NOT_RUN)
  - ตรวจ: ตรวจ dependency/image scans, artifact provenance, log masking และ audit retention
  - Trace: `Dockerfile; .github/workflows/security.yml; .github/workflows/release-evidence.yml`
  - ผ่านเมื่อ: ไม่มี unresolved critical finding; signed artifact และ sensitive data redaction ผ่าน

## P05 — Settlement, reconciliation & operations

Owner: Payments Ops / Finance / QA | Dependencies: P02, P03, P04

Exit gate: ยอดรวมตรงกัน, cutoff/finality/dispute/report ตรวจสอบย้อนหลังได้

- [ ] **P05-01 — Settlement lifecycle & finality** (P0, NOT_RUN)
  - ตรวจ: Trace cutoff/open/close/net position/instruction approval/submit/callback
  - Trace: `settlement/service/; settlement/SettlementLifecycleIntegrationTest.java`
  - ผ่านเมื่อ: valid transitions และ amount/finality ถูก; duplicate/late callback ไม่ settle ซ้ำ
- [ ] **P05-02 — Financial invariants** (P0, NOT_RUN)
  - ตรวจ: ตรวจ pool holds, net totals, funder ledger และ participant balances ก่อน/หลัง E2E
  - Trace: `liquidity/service/PoolService.java; settlement/service/SettlementNetPositionService.java; promotion/reconciliation/`
  - ผ่านเมื่อ: ยอดตาม invariant ที่ Finance อนุมัติตรงกันและไม่มี orphan reservation
- [ ] **P05-03 — Business date & cutoff** (P0, NOT_RUN)
  - ตรวจ: ทดสอบ timezone, T+1, holidays, weekends, rollover และ late transactions
  - Trace: `settlement/SettlementTPlusOneIntegrationTest.java; settlement/calendar/; settlement/SettlementCutoffSchedulerIntegrationTest.java`
  - ผ่านเมื่อ: business/settlement dates ตรง calendar ที่ใช้จริงทั้ง boundary cases
- [ ] **P05-04 — Reconciliation & discrepancies** (P0, NOT_RUN)
  - ตรวจ: ทดสอบ file ingestion, duplicate file, matching, missing/extra/mismatched items
  - Trace: `reconciliation/service/; reconciliation/automation/; reconciliation/ReconciliationIntegrationTest.java`
  - ผ่านเมื่อ: รายงาน discrepancy ถูกและ case closure มี audit; ไม่ปิดผิดโดยอัตโนมัติ
- [ ] **P05-05 — Dispute, reversal & refund** (P0, NOT_RUN)
  - ตรวจ: ทดสอบ DRS, post-settlement dispute, refund/reversal concurrency และ approvals
  - Trace: `src/test/java/com/example/switching/dispute/; fresh dispute QA evidence`
  - ผ่านเมื่อ: refund ไม่ซ้ำและ reconciliation แสดงผลครบ; ops recovery ทำได้จริง
- [ ] **P05-06 — Reports & operator visibility** (P0, NOT_RUN)
  - ตรวจ: ตรวจ dashboard/report/export totals, data freshness และ operator drill
  - Trace: `dashboard/; settlement/service/SettlementOpsReportService.java; docs/openapi/smos-api.yaml`
  - ผ่านเมื่อ: Ops เห็นสถานะจริงและตาม transaction ได้; report amounts ตรง source of truth

## P06 — Performance & capacity

Owner: Performance QA / SRE / DBA | Dependencies: P02–P05 บน UAT ที่แทน PROD ได้

Exit gate: ผ่าน load/burst/soak/settlement benchmark ตาม policy และ workload จริง

- [ ] **P06-01 — UAT production parity** (P0, NOT_RUN)
  - ตรวจ: บันทึก topology, resources, DB/Kafka, dataset, dependencies และ workload model
  - Trace: `performance/; capacity/; config/phase68-performance-policy.yaml`
  - ผ่านเมื่อ: SRE/QA ยอมรับ parity และ capacity assumptions ก่อนใช้ผล benchmark
- [ ] **P06-02 — Smoke & sustained load** (P0, NOT_RUN)
  - ตรวจ: รัน smoke, 2K และ 10K TPS โดยวัด completed business transactions
  - Trace: `performance/scenarios/; performance/scripts/run-k6.sh`
  - ผ่านเมื่อ: ตาม master policy: smoke p95<200ms/error0; 2K p95<300ms/error<0.1%; 10K 60m p95<500ms; ยืนยัน thresholds ก่อนรัน
- [ ] **P06-03 — Burst & backpressure** (P0, NOT_RUN)
  - ตรวจ: รัน 20K burst 15m พร้อม rate-limit, DB pool, downstream capacity และ backlog
  - Trace: `performance/; traffic/; outbox/queue/`
  - ผ่านเมื่อ: ตาม master policy error<0.5%; ไม่ exhaustion; backlog drain ผ่าน recovery budget
- [ ] **P06-04 — Soak & settlement volume** (P0, NOT_RUN)
  - ตรวจ: รัน 8h soak และ settlement 500K; วัด memory, GC, locks, lag และ completion
  - Trace: `performance/; docs/GO_LIVE_CRITICAL_PATH.md`
  - ผ่านเมื่อ: ไม่มี leak/unbounded lag; GC และ batch SLA ผ่านตาม agreed policy
- [ ] **P06-05 — Capacity sign-off** (P0, NOT_RUN)
  - ตรวจ: รวม metrics, raw load logs, autoscaling behavior และ resource headroom
  - Trace: `capacity/; monitoring/; docs/templates/capacity_observations.example.json`
  - ผ่านเมื่อ: signed capacity plan ผูกกับ RC และไม่มี participant/mock shortcut บิดเบือนผล

## P07 — Recovery, DR & observability

Owner: SRE / DBA / SecOps | Dependencies: P04, P05; ทำขนาน P06 ได้

Exit gate: Restore/PITR/DR/failover และ alert delivery ผ่านพร้อมหลักฐาน

- [ ] **P07-01 — Backup integrity & restore** (P0, NOT_RUN)
  - ตรวจ: รัน full backup/verification/restore บน isolated target
  - Trace: `backup/bin/full-backup.sh; backup/bin/; backup/test/integration-smoke.sh`
  - ผ่านเมื่อ: Restore ใช้งานได้จริง; checksum/row count/financial invariants ตรง
- [ ] **P07-02 — PITR & DR targets** (P0, NOT_RUN)
  - ตรวจ: ทดสอบ WAL/PITR และ failover ตาม production topology
  - Trace: `dr/; multi-region-dr/; docs/GO_LIVE_CRITICAL_PATH.md`
  - ผ่านเมื่อ: ตาม master policy RPO<5m/RTO<30m; actual recovery timestamps และ loss proof ครบ
- [ ] **P07-03 — Failure drills & split brain** (P0, NOT_RUN)
  - ตรวจ: ทดสอบ pod kill, Kafka failure, partition, S3 failure และ bank timeout
  - Trace: `dr/scripts/run-dr-suite.sh; chaos/phase73/; resilience/`
  - ผ่านเมื่อ: ไม่มี duplicate/lost transaction; fencing/recovery/replay ผ่านและกลับ healthy
- [ ] **P07-04 — Alerts & traceability** (P0, NOT_RUN)
  - ตรวจ: ยิง alerts ตาม active catalog; ตรวจ delivery, thresholds, dashboards และ correlation
  - Trace: `monitoring/; observability/; slo/; src/main/resources/logback*`
  - ผ่านเมื่อ: on-call รับ alert จริงและ trace inquiry→transfer→outbox→settlement ได้
- [ ] **P07-05 — Operations rehearsal** (P0, NOT_RUN)
  - ตรวจ: ทดสอบ manual retry, incident escalation, cert/key rotation และ scheduler ownership
  - Trace: `operations/; incident/; docs/runbooks/; config/SchedulingConfig.java`
  - ผ่านเมื่อ: runbooks ทำตามได้จริง; owner/on-call/maintenance windows ชัดเจน

## P08 — Authoritative UAT & runtime closure

Owner: QA / Release / Payments Ops | Dependencies: P01–P07

Exit gate: Fresh environment-bound evidence ครบ, ไม่มี critical skip/block, signed GO candidate

- [ ] **P08-01 — Environment preflight** (P0, NOT_RUN)
  - ตรวจ: ตรวจ app readiness, dependencies, access และ DB schema บน candidate environment
  - Trace: `scripts/phase61/; scripts/phase64/; config/phase80/`
  - ผ่านเมื่อ: env readiness ผ่านและบันทึก commit/image/Flyway state ที่ deploy จริง
- [ ] **P08-02 — Authoritative UAT suite** (P0, NOT_RUN)
  - ตรวจ: เลือก authoritative phase chain และรันครบตาม scope โดยไม่แทนด้วย preflight
  - Trace: `scripts/phase74/; scripts/phase78/; scripts/phase80/; docs/GO_LIVE_CRITICAL_PATH.md`
  - ผ่านเมื่อ: signed UAT bundle ครบ core/integration/security/performance/recovery และ required tests ไม่ skip
- [ ] **P08-03 — Phase 98 fresh runtime evidence** (P0, NOT_RUN)
  - ตรวจ: Review collector แล้วรัน health/protected endpoints/Docker/DB capture บน server ด้วย secure credentials
  - Trace: `scripts/phase98/98-production-runtime-evidence-closure.sh`
  - ผ่านเมื่อ: ทุก required check PASS; health UP, exported audit และ deployed migration ยืนยันได้
- [ ] **P08-04 — Phase 99 risk re-score** (P0, NOT_RUN)
  - ตรวจ: คำนวณจาก fresh Phase 98 evidence และตรวจ completeness กับ checklist นี้
  - Trace: `scripts/phase99/99-runtime-evidence-risk-rescore.sh`
  - ผ่านเมื่อ: GO_CANDIDATE และไม่มี FAIL/BLOCKED/WARN/NOT_RUN; Phase 99 ไม่แทนทุก readiness gate
- [ ] **P08-05 — UAT acceptance sign-off** (P0, NOT_RUN)
  - ตรวจ: Review unresolved defects, evidence hashes, change scope และ owner approvals
  - Trace: `docs/templates/; scripts/phase75/; runtime-evidence/`
  - ผ่านเมื่อ: QA/Engineering/Security/SRE/Payments Ops sign-off สำหรับ exact RC

## P09 — Production cutover readiness & Go/No-Go

Owner: Change Manager / Engineering / QA / SecOps / SRE / Product | Dependencies: P08

Exit gate: Immutable RC, signed approvals, cutover baseline และ rollback rehearsal ผ่าน

- [ ] **P09-01 — Immutable release candidate** (P0, NOT_RUN)
  - ตรวจ: ตรวจ signed RC, image digest, SBOM/provenance และ config/schema manifest
  - Trace: `scripts/phase67/67C-immutable-rc-provenance.sh; .github/workflows/release-evidence.yml`
  - ผ่านเมื่อ: RC ที่อนุมัติตรงกับที่จะ deploy; ไม่มี unreviewed drift
- [ ] **P09-02 — Production infra & migration rehearsal** (P0, NOT_RUN)
  - ตรวจ: ตรวจ HA, DB/Kafka/storage/secrets, resource budgets และ dry-run upgrade
  - Trace: `config/production-infrastructure-contract.yaml; scripts/phase67/67B-production-infrastructure-gate.sh; k8s/`
  - ผ่านเมื่อ: Prod contract และ migration rehearsal ผ่านบน production-like copy
- [ ] **P09-03 — Financial cutover baseline** (P0, NOT_RUN)
  - ตรวจ: บันทึก opening balances, pending transfers/outbox, cycles และ reconciliation baseline
  - Trace: `scripts/phase67/67D-financial-cutover-baseline.sh`
  - ผ่านเมื่อ: Finance/Payments Ops ยืนยันยอดและ treatment ของ in-flight transactions
- [ ] **P09-04 — Rollback decision & rehearsal** (P0, NOT_RUN)
  - ตรวจ: ระบุ abort thresholds, traffic reversal, schema compatibility และ data preservation
  - Trace: `scripts/phase67/67G-rollback-decision-engine.sh; .github/workflows/rollback.yml`
  - ผ่านเมื่อ: rollback ผ่าน rehearsal และไม่ทำให้ accepted transactions สูญหาย/ซ้ำ
- [ ] **P09-05 — Signed GO/NO-GO** (P0, NOT_RUN)
  - ตรวจ: รวม approvals, change window, command center และ fresh evidence review
  - Trace: `config/phase79-production-cutover-policy.yaml; docs/templates/PHASE79_PRODUCTION_GO_ATTESTATION.example.json`
  - ผ่านเมื่อ: ทุก required owner ลงนาม; P00–P08 ผ่าน; blockers=0 ก่อนเปิด PROD traffic

## P10 — Go-live execution, hypercare & BAU

Owner: SRE / Payments Ops / Support | Dependencies: P09 signed GO ก่อนเปิด traffic

Exit gate: Canary 5→25→50→100 พร้อม financial checks; hypercare 14 วันและ BAU handover

- [ ] **P10-01 — Canary & staged cutover** (P0, NOT_RUN)
  - ตรวจ: เปิด traffic 5→25→50→100 ตาม approved plan และจับ financial/health metrics
  - Trace: `config/phase79-production-cutover-policy.yaml; scripts/phase67/67E-canary-health-gate.sh; scripts/phase67/67F-progressive-traffic-gate.sh`
  - ผ่านเมื่อ: แต่ละ stage ผ่าน health/financial threshold ก่อนเพิ่ม traffic
- [ ] **P10-02 — Abort handling** (P0, NOT_RUN)
  - ตรวจ: ใช้ command center หยุดหรือ rollback เมื่อพบ financial mismatch/duplicate/security/migration/backlog
  - Trace: `scripts/phase67/67H-command-center-recorder.sh; config/phase79-production-cutover-policy.yaml`
  - ผ่านเมื่อ: ตัดสินใจและ recovery ตาม approved thresholds พร้อม immutable audit
- [ ] **P10-03 — Hypercare & BAU acceptance** (P0, NOT_RUN)
  - ตรวจ: ติดตาม 14 วัน, daily reconciliation, incident trend และส่งมอบ on-call/runbooks
  - Trace: `scripts/phase67/67I-hypercare-tracker.sh; scripts/phase67/67J-bau-acceptance-bundle.sh; scripts/phase81/`
  - ผ่านเมื่อ: hypercare exits ผ่านและ Operations ลงนาม BAU; เงื่อนไขติดตามต้องพร้อมก่อน Go-live

## วิธี execute หลังเตรียม checklist

1. P00: เลือก release candidate และ scope ก่อนใช้ผล audit เป็น Go-live decision.
2. P01: review test harness และ scripts ก่อนรัน; ใช้ isolated disposable DB เท่านั้น เพราะ local fallback ใน AbstractIntegrationTest มี DROP DATABASE ... WITH (FORCE).
3. P02–P05: trace ราย branch แล้ว run focused tests และ UAT ที่ใช้ real participant integrations.
4. P06/P07: เก็บ performance และ recovery evidence ใน production-like UAT ตาม approved workload/drill plan.
5. P08: ทำ authoritative UAT, fresh Phase 98 server-side capture, Phase 99 re-score และ evidence review.
6. P09: signed decision สำหรับ exact RC ก่อน deployment/traffic cutover; P10 execute ตาม approved change window.

คำสั่งอ้างอิง (ยังไม่ได้รันในรอบนี้): `python3 scripts/verify_all_static.py`, `./mvnw verify`, Phase 98/99 collectors. อ่าน prerequisite/side effects ก่อนรัน และส่ง secret ผ่านช่องทางที่ปลอดภัย ไม่ใส่ใน report หรือ shell history.
