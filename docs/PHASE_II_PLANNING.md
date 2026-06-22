# LaoFPS Switching — Phase II Planning

> **Status:** Draft — 2026-06-22
> **Owner:** Switching engineering
> **Predecessor:** Phases 1–55J (89% BRD coverage) complete
> **Scope:** Close the remaining 5 BRD gaps identified in the coverage audit
> **Target:** Phase II complete by Q3 2026; ~10 engineering weeks total

---

## 0. Executive Summary

Phase I (V1–V84, phases 1–55J) delivered the production-ready core of the Lao Fast
Payment Switching (LaoFPS) system: core payment processing, settlement, dispute,
risk, scheme management, monitoring, and the full go-live framework. A diff
against the LaoFPS BRD identified **5 remaining capability gaps** that prevent
the system from being considered 100% BRD-aligned.

| # | Gap | Effort | Priority | Sequence |
|---|---|---|---|---|
| 1 | Request-to-Pay (RTP) as a first-class service | Medium (3 wk) | P0 | Phase II-A |
| 2 | Promotion management as a discrete capability | Medium (2 wk) | P1 | Phase II-B |
| 3 | Cross-border partner adapters (Bakong, NAPAS, UPI) | High (4 wk) | P1 | Phase II-C |
| 4 | Centralised Push Payment orchestrator | Medium (2 wk) | P2 | Phase II-D |
| 5 | Reporting delivery — SFTP + scheduled batch | Low–Medium (1 wk) | P2 | Phase II-E |

Phase II is **sequenced** because Gaps 4 and 1 share state-machine work, and
Gap 2 reuses fee infrastructure. Cross-border (Gap 3) can run in parallel with
Gaps 1, 2, 4 once contracts are stable.

---

## Phase II-A — Request-to-Pay (RTP) Service

### A.1 Business context (from BRD Ch 5, Ch 6.3)

RTP lets a payee request funds from a payer through the switch. The payer
authorises (full / partial / installment) and the system clears it through
the existing transfer rails. Today the codebase has inquiry + transfer + finality
but **no RTP-specific state machine, no request lifecycle, no installment ledger**.

### A.2 Acceptance criteria

1. New `RequestToPayController` exposes `POST /rtp/requests`, `POST /rtp/requests/{id}/authorise`, `POST /rtp/requests/{id}/decline`, `GET /rtp/requests/{id}`.
2. State machine: `PENDING_AUTH → AUTHORISED → SETTLED` (happy path); `DECLINED`, `EXPIRED`, `CANCELLED` (negative); `PARTIALLY_SETTLED`, `INSTALMENT_IN_PROGRESS` (variants).
3. Authorisation modes: **Full**, **Partial** (single payment for less than requested), **Installment** (schedule of N payments).
4. Idempotency: same `request_correlation_id` returns the same RTP.
5. Settlement reuses existing `TransferService` — RTP is a request layer above transfers, not a parallel rail.
6. Expiry policy: default 24h, configurable per participant.
7. Webhook events: `rtp.created`, `rtp.authorised`, `rtp.settled`, `rtp.declined`, `rtp.expired`.

### A.3 Work breakdown

| Step | Deliverable | Files |
|---|---|---|
| A.3.1 | Migration **V85** — `rtp_request`, `rtp_authorisation`, `rtp_installment_schedule` | `src/main/resources/db/migration/V85__rtp_tables.sql` |
| A.3.2 | Entities + repositories | `src/main/java/com/example/switching/rtp/entity/*`, `…/repository/*` |
| A.3.3 | State machine + service | `…/rtp/service/RtpStateMachine.java`, `RtpRequestService.java` |
| A.3.4 | Controller + DTOs | `…/rtp/controller/RequestToPayController.java`, `…/rtp/dto/*` |
| A.3.5 | Outbox events (rtp.*) wired to existing webhook delivery | extend `OutboxEventType` enum, add publishers |
| A.3.6 | Scheduled job — expire stale `PENDING_AUTH` requests every minute | `RtpExpiryScheduler.java` |
| A.3.7 | Integration tests (Testcontainers) — full + partial + installment paths | `src/test/java/com/example/switching/rtp/*IntegrationTest.java` |
| A.3.8 | OpenAPI doc + Postman collection update | `docs/api/rtp.md`, `scripts/curl_rtp_tests.sh` |

### A.4 Estimated effort & risks

- **3 weeks** (1 senior engineer).
- Risks: installment ledger interaction with daily reconciliation (need extra recon query); BRD ambiguity on partial-authorise charging rules.

---

## Phase II-B — Promotion Management

### B.1 Business context (from BRD Ch 14)

Fee module (V65, `TariffGovernanceService`) handles tariffs. Promotions —
temporary fee waivers, cash-back, sponsored-payer programs — are currently
**implicit**: hard-coded inside fee logic. BRD calls for a **separate Promotion
service** with eligibility rules, priority, funding source, and reporting.

### B.2 Acceptance criteria

1. `Promotion` entity: code, name, type (`WAIVER`, `CASHBACK`, `SPONSORED`), start/end, funder, budget cap.
2. `PromotionEligibilityRule`: JSON-DSL or rule-engine record matching participant / channel / amount range / customer segment.
3. `PromotionPriority`: when 2+ promotions apply, deterministic ordering (highest priority + first-created tiebreak).
4. Fee assessment pipeline emits **fee + applied promotions** breakdown.
5. Promotion settlement: a separate `promotion_settlement` ledger so the funder (e.g. BOL, participant marketing budget) is debited correctly.
6. Operator console endpoints: create / suspend / extend / report on promotions.
7. Audit: every promotion application emits a signed audit event.

### B.3 Work breakdown

| Step | Deliverable |
|---|---|
| B.3.1 | Migration **V86** — `promotion`, `promotion_eligibility_rule`, `promotion_application`, `promotion_settlement` |
| B.3.2 | `Promotion` entity + repo + JSON-DSL eligibility evaluator |
| B.3.3 | Refactor `FeeAssessmentService` → returns `FeeAssessmentResult { netFee, promotions: List<PromotionApplication> }` |
| B.3.4 | `PromotionSettlementService` — debits funder, credits payer/payee per promotion type |
| B.3.5 | Operator REST: `POST /promotions`, `PATCH /promotions/{id}/suspend`, `GET /promotions/{id}/report` |
| B.3.6 | Reports: daily promotion usage + funder budget burn-down (reuse Camt054-style template) |
| B.3.7 | Tests: priority ordering, budget cap exhaustion, mid-payment expiry race |

### B.4 Estimated effort & risks

- **2 weeks** (1 engineer + product validation).
- Risk: budget-cap enforcement under concurrent load (use existing outbox + advisory lock pattern from settlement cutoff).

---

## Phase II-C — Cross-Border Partner Adapters

### C.1 Business context (from BRD Ch 13)

LaoFPS must interoperate with regional fast-payment schemes:
**Bakong (Cambodia / NBC)**, **NAPAS (Vietnam)**, **UPI (India / NPCI)**,
**PromptPay (Thailand / NITMX)**. Phase I has folder scaffolding under
`crossborder/adapter/` and partial NITMX work, but no concrete Java adapter
implementations.

### C.2 Acceptance criteria

For **each partner adapter** the deliverable is identical in shape:

1. Adapter implements `CrossBorderRailAdapter` interface (define if absent).
2. Outbound: `submit(CrossBorderInstruction)` → returns `RailTransactionRef`.
3. Inbound: webhook / polling endpoint translates partner messages to `CrossBorderInstructionEvent`.
4. Idempotency + replay: every adapter persists `cross_border_rail_message` with `(rail, external_ref)` unique key.
5. mTLS + signed payloads — reuse `WebhookHttpSender` patterns.
6. Compliance: AML screening invoked **before** outbound submit; sanctions hit fails closed.
7. FX quote: integrate with existing `FxQuoteService` for non-LAK pairs.
8. Settlement reconciliation: daily Camt.053-equivalent comparison against rail statement.

### C.3 Per-rail specifics

| Rail | Protocol | Auth | Notes |
|---|---|---|---|
| Bakong | REST / JSON | OAuth2 client-credentials, mTLS | NBC-issued partner ID + Bakong KHQR / phone routing |
| NAPAS | ISO 20022 over MQ or REST | mTLS + HMAC | Coordinate currency pair LAK↔VND via FX gateway |
| UPI | UPI International (NPCI) | NPCI-issued cert + UPI ID resolution | Inward-only initially; outward needs NPCI accreditation |
| PromptPay (NITMX) | ISO 8583 / ISO 20022 | mTLS + signature | Already partially modelled — finalise inbound flow |

### C.4 Work breakdown (per rail)

| Step | Deliverable |
|---|---|
| C.4.1 | Adapter class implementing `CrossBorderRailAdapter` |
| C.4.2 | DTOs + Jackson mappers for that rail's message format |
| C.4.3 | Inbound webhook controller / scheduled poller |
| C.4.4 | Migration extending `cross_border_rail_message` if new fields needed |
| C.4.5 | Wiremock-driven integration tests covering success / decline / timeout / partner-replay |
| C.4.6 | Recon script comparing daily statement vs internal ledger |

### C.5 Estimated effort & risks

- **4 weeks total** (parallelisable across 2 engineers — 2 rails each).
- Risks: partner sandbox availability (esp. UPI International — long onboarding); FX rate sourcing on weekends; settlement netting holiday calendar mismatches.

---

## Phase II-D — Centralised Push Payment Orchestrator

### D.1 Business context

Today, `TransferService`, `QrPaymentService`, `BillPaymentService` each
contain their own copies of: timeout handling, retry schedule, finality
decision (Option A immediate vs Option B asynchronous), webhook fan-out. BRD
Ch 6.2 calls for **a single configurable Push Payment orchestrator** so
business policy is changed in one place.

### D.2 Acceptance criteria

1. New `PushPaymentOrchestrator` owns the lifecycle of every "push" payment regardless of channel.
2. Each channel (transfer / QR / bill / RTP-authorised / cross-border outbound) calls `orchestrator.start(PushPaymentRequest)`.
3. Configurable policy bundle: `timeout`, `retrySchedule`, `finalityMode`, `webhookEventNames`, `idempotencyTtl`.
4. Existing services keep their channel-specific validation but delegate state transitions to the orchestrator.
5. **No behaviour change** for happy paths — verified by re-running full integration suite.
6. Single place to add Phase II-A RTP, Phase II-C cross-border, future channels.

### D.3 Work breakdown

| Step | Deliverable |
|---|---|
| D.3.1 | Extract common state-machine interface `PaymentLifecycle` |
| D.3.2 | New `PushPaymentOrchestrator` consuming `PaymentLifecycle` |
| D.3.3 | Refactor `TransferService` → uses orchestrator (parity tests) |
| D.3.4 | Refactor `QrPaymentService` → uses orchestrator |
| D.3.5 | Refactor `BillPaymentService` → uses orchestrator |
| D.3.6 | Migration **V87** — `push_payment_policy` config table (one row per channel) |
| D.3.7 | Operator endpoint to update policy (governed by ConfigurationChangeRequest flow) |
| D.3.8 | Test parity report — old vs new state transition logs identical |

### D.4 Estimated effort & risks

- **2 weeks** (1 senior engineer).
- Risks: refactor regression — mitigated by exhaustive integration + curl E2E parity tests.

---

## Phase II-E — Reporting Delivery (SFTP + Scheduled Batch)

### E.1 Business context (BRD Ch 15)

Today, reports (Camt054, dispute, AML, settlement) are retrieved on-demand
via REST or downloaded from the operator portal. BRD requires **scheduled
generation + delivery channels** so participants and regulators receive
reports automatically (SFTP push, S3 drop, email link).

### E.2 Acceptance criteria

1. `ReportDeliverySchedule` entity: report type, recipient, cron, channel (`SFTP`, `S3`, `EMAIL_LINK`), retention.
2. Scheduler reuses Quartz/Spring `@Scheduled` infra already wired by `SettlementCutoffScheduler`.
3. Generation uses existing report services (do not duplicate logic).
4. Delivery channels:
   - **SFTP**: configurable host/port/path, SSH key auth, atomic upload (`.tmp` → rename).
   - **S3**: reuse archive minio creds, prefix per participant.
   - **EMAIL_LINK**: notification with signed download URL (24h TTL).
5. Idempotency: same generation timestamp produces the same artifact hash; redelivery does not regenerate.
6. Audit: every delivery emits `report.delivered` signed event.
7. Failed deliveries retry with exponential backoff (reuse outbox retry pattern).

### E.3 Work breakdown

| Step | Deliverable |
|---|---|
| E.3.1 | Migration **V88** — `report_delivery_schedule`, `report_delivery_run`, `report_artifact` |
| E.3.2 | `ReportDeliveryScheduler` (Spring `@Scheduled` per row) |
| E.3.3 | `SftpDeliveryService` (Apache MINA SSHD client) |
| E.3.4 | `S3DeliveryService` (extend existing MinIO client) |
| E.3.5 | `EmailLinkDeliveryService` (signed URL + existing notification pipeline) |
| E.3.6 | Operator REST + portal UI for schedule management |
| E.3.7 | Integration tests using **Testcontainers SFTP server** + MinIO |

### E.4 Estimated effort & risks

- **1 week** (1 engineer).
- Risks: SSH host-key management across UAT/prod; legal review of regulator delivery channel choice.

---

## Cross-Cutting Concerns (apply to all 5)

- **Migrations** — V85 (RTP), V86 (Promotion), V87 (PushPolicy), V88 (Reporting). Reserve V89–V99 for unforeseen Phase II patches.
- **Schema parity** — every new SHA-256 column must be `VARCHAR(64)` (lesson from V84 incident).
- **Observability** — every new service registers metrics with the existing `OperationalMetricsCollector` (counter, histogram, error rate).
- **Audit** — every state transition emits a signed event consumed by the existing audit chain.
- **Test coverage** — each Phase II module must add ≥ 1 unit, 1 slice, 1 integration test (mirroring Phase 53C–53J pattern).
- **Documentation** — update [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md), add runbook in `docs/runbooks/`, add OpenAPI spec.
- **Static verifiers** — add `scripts/verify_phase_ii_X.py` analogous to `verify_phases_43_52_static.py`.
- **CI gates** — add `.github/workflows/phase-ii-static-contract.yml`.

---

## Timeline

```
Week 1  ──┬── II-A RTP schema + state machine
Week 2    │
Week 3  ──┘   II-A integration tests + APIs
Week 4  ──┬── II-B Promotion (parallel start of II-C)
Week 5    │
Week 6  ──┴── II-B closeout
Week 7  ──┬── II-C cross-border adapters (2 engineers, parallel)
Week 8    │   II-D Push Payment orchestrator
Week 9    │
Week 10 ──┴── II-E reporting + final test parity + go-live rehearsal
```

| Phase | Duration | Engineers | Start | End |
|---|---|---|---|---|
| II-A RTP | 3 wk | 1 | Wk 1 | Wk 3 |
| II-B Promotion | 2 wk | 1 | Wk 4 | Wk 5 |
| II-C Cross-Border | 4 wk | 2 | Wk 4 | Wk 7 |
| II-D Push Orchestrator | 2 wk | 1 | Wk 7 | Wk 8 |
| II-E Reporting | 1 wk | 1 | Wk 9 | Wk 9 |
| Phase II hardening + tests | 1 wk | all | Wk 10 | Wk 10 |

**Total calendar:** 10 weeks
**Total engineer-weeks:** ~14 (3+2+8+2+1+2 stabilisation overhead)

---

## Acceptance / Exit Criteria for Phase II

Phase II is **complete** when:

1. ✅ All 5 gaps have feature flag on by default in `application.yml`.
2. ✅ Maven `./mvnw verify` — green (0 errors, 0 failures).
3. ✅ Static verifiers — `scripts/verify_all_static.py` green.
4. ✅ E2E suite — `scripts/run_tests.sh --with-junit` green.
5. ✅ Production readiness gate — `scripts/execute-and-verify/00-run-all.sh` green.
6. ✅ BRD coverage re-audit ≥ 98%.
7. ✅ All new endpoints documented in OpenAPI + curl test scripts.
8. ✅ Runbooks signed off.
9. ✅ Phase 54-style certification campaign re-run on new modules.
10. ✅ Phase 55-style go-live rehearsal sign-off.

---

## Open Questions for Product / BoL

1. **RTP installment defaults** — what is the maximum N for installment schedules? Per BRD or per participant?
2. **Promotion funding** — can BoL fund switching-wide promotions, or only participants?
3. **Cross-border priority** — which rail goes first if budget allows only 2 in Phase II? (Recommend: NITMX finalise → Bakong → NAPAS → UPI)
4. **Reporting recipients** — list of regulators + participants entitled to scheduled delivery.
5. **Push orchestrator backward-compat** — is a Phase II.5 deprecation window required for old `TransferService.start()` signature?

---

## References

- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md) — Phase I progress (1–55J)
- [docs/database-and-architecture-design.md](database-and-architecture-design.md) — current schema overview
- [scripts/execute-and-verify/README.md](../scripts/execute-and-verify/README.md) — readiness gates
- LaoFPS BRD v1.0 (`/Users/macbookpro/Downloads/LaoFPS_Combined_Organized_BRD.docx`) — source of truth

---

*Document version: 0.1 (draft). Update after stakeholder review.*
