# Project Development Status Summary

Last updated: 2026-05-22 (v4.3)
Project path: `/Users/macbookpro/Desktop/Switching`

## Executive Summary

This project is a Java 21 / Spring Boot backend for a **LaoFP-compliant ISO 20022 Payment Switching System**. It has moved far beyond a prototype: all core domain modules are implemented, integration-tested against a real PostgreSQL 16 database (Testcontainers), and hardened for production-readiness through multiple compliance phases.

### Verified build status (2026-05-22)

```bash
./mvnw -q test
```

```text
Tests run: 153 | Failures: 0 | Errors: 0 | Skipped: 0
BUILD SUCCESS
```

```bash
./mvnw -q -DskipTests compile
```

```text
BUILD SUCCESS
```

- **Java source files:** 382
- **Flyway migrations:** V1–V23 (all PostgreSQL)
- **Integration test classes:** ~33 test files, 153 test methods
- **Database engine:** PostgreSQL 16 (migrated from MySQL in v3.4)

---

## Technology Stack

| Component | Version / Details |
|-----------|-------------------|
| Java | 21 |
| Spring Boot | 4.0.3 |
| Build tool | Maven |
| Database | PostgreSQL 16 (primary + read replica + warm archive DB) |
| Object storage | MinIO (S3-compatible, COLD tier) |
| ORM | Spring Data JPA / Hibernate 6 |
| JDBC | Spring JdbcTemplate (partitioned table inserts) |
| DB migration | Flyway (PostgreSQL dialect, V1–V23) |
| Security | Spring Security 6 — API key (SHA-256) + OAuth 2.0 client_credentials + mTLS + HMAC-SHA256 request signing |
| Observability | Micrometer + Prometheus + logstash-logback-encoder (JSON structured logs) |
| Messaging format | ISO 20022 — ACMT.023, PACS.008, PACS.002, CAMT.056 |
| Encryption | AES/GCM per-message symmetric encryption |
| Containerization | Docker / Docker Compose (multi-stage Dockerfile, non-root user) |
| Kubernetes | Manifests in `k8s/` — Deployment, Service, ConfigMap, Secret, HPA |
| Testing | JUnit 5 + Testcontainers PostgreSQL 16 + Spring Boot Test |

---

## Persistence Topology

```
HOT  →  switching-postgres         (primary, port 5433, read/write)
HOT  →  switching-postgres-replica (streaming replica, port 5435, read-only)
WARM →  switching-postgres-archive (archive DB, port 5434, cold metadata)
COLD →  switching-minio            (S3 object storage, port 9000/9001)
```

- Primary DB has **19 Flyway-managed tables + partitioned tables** (`transactions`, `inquiries`, `transaction_events`, `payment_flows`, `settlement_items`, `iso_validation_errors`, `reconciliation_items`)
- Archive DB has **11 archive tables + `object_storage` schema** (objects, manifests, retention_policies)
- MinIO bucket: `switching-archive` (versioning enabled)
- `ArchiveWorkerService` runs daily — archives rows 90+ days old, uploads ISO payloads to MinIO, verifies counts, drops old partitions
- `PartitionMaintenanceService` runs daily — creates forward partitions 90 days ahead

---

## Main Package Structure

```
src/main/java/com/example/switching/
├── audit/              — AuditLogService, AuditLogQueryService, AuditLogController
├── common/             — GlobalExceptionHandler, ErrorCatalog, ErrorClassifier,
│                         MaskingUtil, RequestIdFilter, AuditActorUtil
├── config/             — JpaConfig, SchedulingConfig, OpenApiConfig, WebConfig,
│                         ArchiveJdbcConfig (secondary datasource), MinioConfig
├── connector/          — ConnectorRegistry, ConnectorConfigService,
│                         GenericMockConnector, GenericHttpConnector, GenericMqConnector
├── dashboard/          — DashboardOverviewService, DashboardController
├── fpre/               — FpreOperationsService, FpreOperationsController,
│                         FpreRetryScheduleService (DTOs + retry/reversal logic)
├── idempotency/        — IdempotencyService, IdempotencyFilter
├── inquiry/            — CreateInquiryService, InquiryLookupService,
│                         InquiryController, InquiryEntity, InquiryStatusHistoryEntity
├── iso/                — IsoMessageService, InboundPacs008PersistenceService,
│                         InboundPacs002MessageService, IsoMessageCryptoService,
│                         Pacs008XmlBuilder, Pacs002XmlBuilder, Camt056XmlBuilder,
│                         IsoXmlValidator, IsoInquiryInboundService
├── maintenance/        — AggregationService, AggregationScheduler,
│                         ArchiveWorkerService, PartitionMaintenanceService,
│                         SchedulerLockService, ArchiveProperties
├── operations/         — 20+ operation controllers + services:
│   controller/         — OperationsHealthController, OperationsDashboardController,
│                         OperationsTransferController, OperationsTransactionController,
│                         OperationsIsoController, OperationsAuditController,
│                         OperationsOutboxController, OperationsBankController,
│                         OperationsConnectorController, OperationsSettlementController,
│                         OperationsReconciliationController,
│                         OperationsTransactionEventsController,
│                         OperationsAggregationController
│   service/            — OperationsHealthService, DashboardOverviewService,
│                         OperationsBankStatusService, OperationsBankOnboardingService,
│                         OperationsConnectorHealthService, OperationsTransferService,
│                         OperationsOutboxFailureService, OperationsIsoService,
│                         OperationsAuditLogService
├── outbox/             — OutboxTransactionService, OutboxProcessorService,
│                         OutboxIsoMessageDispatchService, OutboxRecoveryService,
│                         OutboxDispatchWorker, OutboxRecoveryWorker,
│                         OutboxFailureClassificationService, OutboxRetryScheduleService,
│                         OutboxManualRetryService, OutboxEventEntity
├── participant/        — ParticipantService, ParticipantManagementService,
│                         ParticipantController, ParticipantEntity,
│                         ParticipantCredentialService, ParticipantCredentialController
├── reconciliation/     — ReconciliationFileEntity, ReconciliationItemEntity,
│                         ReconciliationFileRepository, ReconciliationItemRepository,
│                         ReconciliationFileService, ReconciliationMatchingService,
│                         ReconciliationDiscrepancyService, ReconciliationController
├── routing/            — RoutingService, RoutingRuleManagementService,
│                         RoutingRuleController, RoutingRuleEntity
├── security/           — OAuthTokenService, OAuthTokenController, OAuthTokenFilter,
│                         MtlsCertificateValidator, MtlsFilter,
│                         RequestSignatureFilter, ApiKeyAuthFilter,
│                         ApiKeyService, ApiKeyController
├── settlement/         — SettlementCycleEntity, SettlementPositionEntity,
│                         SettlementCycleRepository, SettlementPositionRepository,
│                         SettlementCycleService, SettlementBatchService,
│                         SettlementNetPositionService, SettlementController
└── transfer/           — CreateTransferService, TransferTraceService,
                          TransferStateMachineService, TransactionEventPublisher,
                          PaymentFlowTracker, TransferController, TransferEntity,
                          TransferStatusHistoryEntity, TransferRepository
```

---

## Business Flow

```
Bank / PSP
    │
    ├─► POST /api/iso20022/acmt023 (ISO Inquiry — ACMT.023)
    │       IsoInquiryInboundService → InquiryEntity [business_date partition]
    │       → inquiry_status_history → AuditLog
    │
    ├─► POST /api/iso20022/pacs008 (ISO Transfer — PACS.008)
    │   POST /api/transfers          (JSON Transfer)
    │       CreateTransferService
    │           → InquiryEntity [USED]
    │           → TransferEntity [ACCEPTED]
    │           → TransferStateMachineService
    │           → IsoMessageEntity + iso_message_payloads
    │           → OutboxEventEntity [PENDING]
    │           → TransactionEventPublisher [TRANSFER_INITIATED]
    │           → PaymentFlowTracker [INITIATED]
    │           → AuditLog
    │
    └─► Scheduled: OutboxDispatchWorker (every 3s, batch 50)
            OutboxProcessorService
                → claim [PENDING→PROCESSING]
                → TransactionEventPublisher [TRANSFER_DISPATCHED]
                → OutboxIsoMessageDispatchService
                    → ConnectorRegistry → GenericHttpConnector / MockConnector
                    → dispatchEncryptedIsoMessage(PACS.008)
                    → parsePacs002Response
                ──────────────────────────────────────
                SUCCESS path:
                    → TransferStateMachineService [ACCEPTED→SETTLED]
                    → TransactionEventPublisher [TRANSFER_SETTLED]
                    → PaymentFlowTracker [SETTLED]
                    → outbox_attempts [SUCCESS]
                    → AuditLog
                ──────────────────────────────────────
                BUSINESS FAILURE path:
                    → OutboxFailureClassificationService
                    → TRANSIENT / PERMANENT_BUSINESS / AMBIGUOUS
                    → retry or reject
                    → TransferStateMachineService [ACCEPTED→REJECTED]
                    → TransactionEventPublisher [TRANSFER_REJECTED / RETRY_SCHEDULED]
                    → PaymentFlowTracker [FAILED if terminal]
                    → outbox_attempts [FAILED / RETRY_SCHEDULED]
```

---

## What Is Implemented

### Core Payment Flow — ✅ Complete

| Module | Status | Key Classes |
|--------|--------|-------------|
| JSON Inquiry | ✅ Done | `CreateInquiryService`, `InquiryController` |
| JSON Transfer | ✅ Done | `CreateTransferService`, `TransferController` |
| ISO ACMT.023 Inquiry | ✅ Done | `IsoInquiryInboundService` |
| ISO PACS.008 Transfer | ✅ Done | `InboundPacs008PersistenceService` |
| ISO PACS.002 Response | ✅ Done | `InboundPacs002MessageService` |
| ISO CAMT.056 Recall | ✅ Done | `Camt056XmlBuilder` |
| Outbox dispatch | ✅ Done | `OutboxProcessorService`, `OutboxDispatchWorker` |
| Outbox recovery (stuck) | ✅ Done | `OutboxRecoveryService`, `OutboxRecoveryWorker` |
| Transfer state machine | ✅ Done | `TransferStateMachineService` (ACCEPTED→SETTLED/REJECTED) |
| Routing | ✅ Done | `RoutingService`, cache + management APIs |
| Participant management | ✅ Done | `ParticipantService`, `ParticipantManagementService` |
| Connector registry | ✅ Done | HTTP + MQ + Mock connectors registered |
| Idempotency | ✅ Done | `IdempotencyService`, `IdempotencyFilter` |
| Audit logging | ✅ Done | `AuditLogService` — all domain events logged |
| Masking / PII | ✅ Done | `MaskingUtil` masks account numbers in audit + ops views |

### Security — ✅ P9 Complete

| Feature | Status |
|---------|--------|
| API key auth (SHA-256 hash, expiry, rotation) | ✅ Done |
| OAuth 2.0 client_credentials grant + Bearer token | ✅ Done |
| mTLS certificate validation (X.509, SHA-256 fingerprint) | ✅ Done |
| HMAC-SHA256 request signing (`X-Request-Signature`) | ✅ Done |
| Role-based access: BANK / OPS / ADMIN | ✅ Done |
| Participant credential rotation + cert register/revoke | ✅ Done |
| PSP auto-suspension on repeated failures | ✅ Done |
| Demo key disable on prod profile startup | ✅ Done |

### FPRE — 🟡 P10 at 95%

| Feature | Status |
|---------|--------|
| Failure classification (TRANSIENT / PERMANENT_BUSINESS / AMBIGUOUS) | ✅ Done |
| Exponential backoff retry schedule (5 attempts, ±10% jitter) | ✅ Done |
| Ambiguous credit check via connector endpoint_url | ✅ Done |
| Auto-reversal (CAMT.056) for terminal AMBIGUOUS | ✅ Done |
| FPRE read APIs (`/v1/transfers/*/retry-status`, `/pending`, `/failed`) | ✅ Done |
| LFP-FPRE-001 / 002 / 003 error mappings | ✅ Done |
| ISO payload persistence + outbox hydration | ✅ Done |
| Transaction event publishing (INITIATED/DISPATCHED/SETTLED/REJECTED) | ✅ Done |
| Payment flow tracking (INITIATED→DISPATCHED→SETTLED/FAILED) | ✅ Done |
| outbox_attempts recording (attempt history per dispatch) | ✅ Done |

### Settlement Engine — 🟡 P14 at 35%

| Feature | Status |
|---------|--------|
| `SettlementCycleEntity` + `SettlementCycleRepository` | ✅ Done |
| `SettlementPositionEntity` + `SettlementPositionRepository` | ✅ Done |
| `SettlementCycleService` (OPEN→CLOSED→SETTLED, max 4/day, cycleRef SC-yyyyMMdd-Cn) | ✅ Done |
| `SettlementBatchService` (batch transactions → `settlement_items` + upsert `settlement_positions`) | ✅ Done |
| `SettlementNetPositionService` (multilateral netting `markAllSettledByCycleId`) | ✅ Done |
| `SettlementController` (`POST /cycles`, `/batch`, `/close`, `/settle`, `GET /cycles`) | ✅ Done |
| RTGS integration / external DNS settlement | ⬜ Not started |
| Settlement reconciliation vs external CSM | ⬜ Not started |

### Reconciliation — ✅ Full Package Implemented

| Feature | Status |
|---------|--------|
| `ReconciliationFileEntity` + `ReconciliationFileRepository` | ✅ Done |
| `ReconciliationItemEntity` (partitioned — JdbcTemplate) + `ReconciliationItemRepository` | ✅ Done |
| `ReconciliationFileService` (RECEIVED→PROCESSING→COMPLETED/FAILED) | ✅ Done |
| `ReconciliationMatchingService` (MATCHED / UNMATCHED / DISPUTED, re-match) | ✅ Done |
| `ReconciliationDiscrepancyService` (discrepancy report) | ✅ Done |
| `ReconciliationController` (import, items, rematch, discrepancies) | ✅ Done |

### Aggregation Jobs — ✅ Implemented

| Feature | Status |
|---------|--------|
| `AggregationService` — upsert `daily_transaction_summary`, `hourly_transaction_summary`, `inquiry_daily_summary` | ✅ Done |
| `AggregationScheduler` — daily 00:30 + hourly HH:05, guarded by `SchedulerLockService` | ✅ Done |
| `OperationsAggregationController` — manual trigger endpoints | ✅ Done |

### Archive & Storage — ✅ Implemented

| Feature | Status |
|---------|--------|
| `ArchiveWorkerService` — daily archive job with MinIO upload + manifest | ✅ Done |
| `PartitionMaintenanceService` — 90-day forward partition window | ✅ Done |
| `SchedulerLockService` — DB advisory lock for distributed scheduling | ✅ Done |
| Object storage schema (`object_storage.objects/manifests/retention_policies`) | ✅ Done |

### Observability — 🟡 P6 at 55%

| Feature | Status |
|---------|--------|
| JSON structured logging (logstash-logback-encoder) | ✅ Done |
| Micrometer metrics (`/actuator/prometheus`) | ✅ Done |
| Request-level audit logging | ✅ Done |
| Prometheus + Grafana setup | ⬜ Infrastructure only |
| ELK stack integration | ⬜ Infrastructure only |

---

## Flyway Migrations

| Version | Description |
|---------|-------------|
| V1 | Core tables: transfers, outbox_events, idempotency_records, audit_logs |
| V2 | Seed data: participants, routing_rules, connector_configs, api_keys |
| V3 | Inquiry tables: inquiries, inquiry_status_history |
| V4 | Unique constraint: transfers(inquiry_ref) |
| V5 | updated_at: outbox_events |
| V6 | iso_messages, iso_inquiries |
| V7 | updated_at: idempotency_records |
| V8 | participants, routing_rules (production schema) |
| V9 | connector_configs |
| V10 | Partitioned tables: transactions, payment_flows, transaction_events, iso_validation_errors |
| V11 | FPRE tables: outbox_attempts, transfer_status_history, iso_message_payloads |
| V12 | Archive tables (warm archive metadata) |
| V13 | Settlement tables: settlement_cycles, settlement_items, settlement_positions |
| V14 | Reconciliation tables: reconciliation_files, reconciliation_items (partitioned) |
| V15 | Summary tables: daily_transaction_summary, hourly_transaction_summary, inquiry_daily_summary |
| V16 | Connector call archive metadata |
| V17 | Object storage schema (object_storage.objects, manifests, retention_policies) |
| V18 | Object storage grants |
| V19 | iso_validation_errors archive |
| V20 | oauth_clients (seeded with BANK_A/B clients) |
| V21 | psp_certificates (cert_id, psp_id, cert_fingerprint, status ACTIVE/REVOKED) |
| V22 | Seed psp_certificates: BANK_A/B active certificate fingerprints |
| V23 | outbox_events: failure_class + will_retry columns |

---

## Integration Test Suite (153/153 PASS)

| Test Class | Count | What It Covers |
|------------|-------|----------------|
| IsoInquiryStatusHistoryIntegrationTest | 3 | ELIGIBLE/REJECTED/USED status history |
| IsoInquiryConcurrentIdempotencyIntegrationTest | 2 | Concurrent ACMT.023 race (ON CONFLICT DO NOTHING) |
| IdempotencyIntegrationTest | 3 | Duplicate request handling |
| OutboxBackoffIntegrationTest | 4 | Retry backoff + next_retry_at |
| OutboxConcurrentDispatchIntegrationTest | 2 | Concurrent outbox claiming |
| OutboxWorkerShutdownTest | 4 | Graceful shutdown in-flight |
| ApiKeyRotationIntegrationTest | 4 | API key hash rotation + expiry |
| SecurityAuthorizationIntegrationTest | 8 | Role-based access (BANK/OPS/ADMIN + 401/403 paths) |
| RequestSignatureIntegrationTest | 4 | HMAC-SHA256 signing enforcement |
| OAuthTokenFlowIntegrationTest | 5 | OAuth token create/validate/revoke |
| OAuthTokenFilterIntegrationTest | 4 | Bearer token filter + ROLE_BANK |
| MtlsValidationIntegrationTest | 4 | mTLS cert validation (no cert, revoked, active) |
| ParticipantCredentialRotationIntegrationTest | 4 | Secret rotate + cert register/revoke + suspend |
| FpreOperationsServiceIntegrationTest | (multi) | FPRE APIs (pending, failed, retry-status) |
| FpreRetryScheduleIntegrationTest | (multi) | Retry schedule + backoff jitter |
| FpreAutoReversalIntegrationTest | (multi) | CAMT.056 auto-reversal |
| PspAutoSuspensionIntegrationTest | (multi) | Auto-suspend on repeated failures |
| FpreAmbiguousCheckIntegrationTest | (multi) | Ambiguous credit check → settle/retry |
| FpreErrorMappingTest | (multi) | LFP-FPRE-001/002/003 error mappings |

---

## API Surface (Key Endpoints)

### Bank-Facing (ROLE: BANK / ADMIN)

```
POST   /api/inquiries                          — Create inquiry (JSON path)
GET    /api/inquiries/{ref}                    — Inquiry lookup
POST   /api/transfers                          — Create transfer (JSON path)
GET    /api/transfers                          — Transfer list
GET    /api/transfers/{ref}                    — Transfer detail
GET    /api/transfers/{ref}/trace              — Transfer trace
POST   /api/iso20022/pacs008                   — ISO PACS.008 inbound
POST   /api/iso20022/acmt023                   — ISO ACMT.023 inquiry inbound
GET    /api/iso-messages, /api/iso-messages/{key}
GET    /api/outbox-events
POST   /api/outbox-events/{id}/retry
POST   /v1/oauth/token                         — OAuth client_credentials
POST   /v1/oauth/token/revoke
GET    /v1/transfers/{txnId}/retry-status      — FPRE retry status
GET    /v1/transfers/{txnId}/retry-history     — FPRE retry history
GET    /v1/transfers/pending                   — FPRE pending (BANK-scoped)
GET    /v1/transfers/failed                    — FPRE failed (BANK-scoped)
GET    /v1/fpre/health                         — FPRE health summary
```

### Operations (ROLE: OPS / ADMIN)

```
GET    /api/operations/health
GET    /api/operations/dashboard-summary
GET    /api/operations/transactions
GET    /api/operations/transfers, /{ref}, /{ref}/trace
GET    /api/operations/iso-messages, /iso-inquiries, /iso-inquiries/{ref}
GET    /api/operations/audit-logs
GET    /api/operations/outbox-failures, /outbox-stuck
POST   /api/operations/outbox-failures/retry-all     (ADMIN only)
POST   /api/operations/outbox-stuck/recover-all      (ADMIN only)
GET    /api/operations/bank-status
POST   /api/operations/bank-onboarding               (ADMIN only)
GET    /api/operations/connectors/health
POST   /api/operations/connectors/{name}/test        (ADMIN only)
POST   /api/operations/settlement/cycles
GET    /api/operations/settlement/cycles, /cycles/{ref}
POST   /api/operations/settlement/batch
POST   /api/operations/settlement/close
POST   /api/operations/settlement/settle             (ADMIN only)
POST   /api/operations/reconciliation/files
GET    /api/operations/reconciliation/files, /files/{ref}, /files/{ref}/items
POST   /api/operations/reconciliation/files/{ref}/items
POST   /api/operations/reconciliation/rematch        (ADMIN only)
GET    /api/operations/reconciliation/discrepancies
GET    /api/operations/transaction-events/{ref}
GET    /api/operations/payment-flows/{ref}
GET    /api/operations/transaction-events?date=&type=
POST   /api/operations/aggregation/run               (ADMIN only)
POST   /api/operations/aggregation/run/{date}        (ADMIN only)
```

### Admin / Participant Management (ROLE: ADMIN)

```
GET/POST       /api/participants
PATCH          /api/participants/{bankCode}
GET/POST/PATCH /api/routing-rules, /routing-rules/{code}
GET/POST/PATCH /api/connector-configs, /connector-configs/{name}
GET/POST       /api/api-keys
POST           /api/api-keys/{id}/rotate, /disable
POST           /v1/participants/{pspId}/credentials/rotate
POST           /v1/participants/{pspId}/certificates/register
DELETE         /v1/participants/{pspId}/certificates/{certId}
```

---

## Production Readiness Estimate (2026-05-22)

| Area | Estimate |
|------|----------|
| Core payment flow (inquiry → transfer → outbox → ISO dispatch → response) | 95% |
| Security (API key + OAuth + mTLS + signing + RBAC) | 90% |
| FPRE compliance (retry/reversal/ambiguous/suspension) | 95% |
| Settlement engine | 35% |
| Reconciliation | 70% |
| Aggregation / reporting | 60% |
| Archive / storage automation | 75% |
| Observability (structured logs + metrics) | 55% |
| Unit test coverage | ~10% (integration tests only) |
| Real connector implementation | 40% (HTTP/MQ wired, real TLS/certs pending) |
| ISO XML schema validation | 10% (stub — NOT_VALIDATED) |
| K8s / deployment | 80% (manifests done, staging drill pending) |
| **Overall production readiness** | **~65–70%** |

### What Remains Before Production Go-Live

| Priority | Work Item |
|----------|-----------|
| HIGH | Real `AccountLookupService` (currently mock — accepts any account) |
| HIGH | ISO XSD schema validation (IsoXmlValidator stub always returns NOT_VALIDATED) |
| HIGH | MinIO bean + archiveJdbcTemplate wiring (compile-time only, not runtime-verified) |
| HIGH | Prod SSL: `sslmode=require` in JDBC URL, backup/restore drill, PITR policy |
| MED | Settlement completion: RTGS / DNS external settlement integration |
| MED | Webhook dispatcher (P12) — transaction_events → member bank webhooks |
| MED | Unit test coverage: ~10%, critical gaps in dashboard, fpre, audit, settlement |
| MED | Prometheus + Grafana + ELK setup (infrastructure) |
| LOW | QR code service (P15) |
| LOW | Cross-border payment (P17) |
| LOW | AML / CFT risk engine (P19) |
| LOW | Performance certification 2K→10K TPS (P20) |

---

## Useful Commands

```bash
# Run full test suite
./mvnw -q test

# Compile only (skip tests)
./mvnw -q -DskipTests compile

# Start full local stack (primary DB + replica + archive + MinIO)
docker compose up -d

# Check DB health
docker exec switching-postgres pg_isready -U switching_app -d switching

# Check replica lag
docker exec switching-postgres-read-replica psql -U switching_app -d switching \
  -c "SELECT pg_is_in_recovery(), now() - pg_last_xact_replay_timestamp() AS replication_lag;"

# Manual aggregation trigger (OPS key)
curl -s -X POST http://localhost:8080/api/operations/aggregation/run \
  -H "X-API-Key: sk-ops-switching-2026"

# Settlement cycle (open new intraday cycle)
curl -s -X POST http://localhost:8080/api/operations/settlement/cycles \
  -H "X-API-Key: sk-ops-switching-2026" \
  -H "Content-Type: application/json" \
  -d '{"cycleType":"INTRADAY"}'

# Check transaction events for a transfer
curl -s "http://localhost:8080/api/operations/transaction-events/TXN-REF?date=$(date +%F)" \
  -H "X-API-Key: sk-ops-switching-2026"
```
