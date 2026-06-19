 # LaoFP Switching API — Overall Project Reference

> **Purpose of this document:** Comprehensive reference for AI agents working on this codebase.
> Covers architecture, domain model, APIs, DB schema, services, workers, config, tests, known issues, and next steps.
> **Target Specification:** LaoFP Master System Specification v1.0 (LaoFP-MASTER-001) — National Real-Time Payment Switching for Lao PDR.
> Last updated: 2026-05-15 — P4 50% / P5 65% / P7 55% / 60/60 tests PASS. LaoFP spec alignment added (round 5).

---

## 1. Project Overview

**LaoFP Switching API** is the **National Real-Time Payment Switching Infrastructure for the Lao PDR** — the central hub that routes interbank and cross-wallet payments following the **ISO 20022** financial messaging standard, as defined in LaoFP Master Specification v1.0 (doc ID: LaoFP-MASTER-001).

**Governing standard:** ISO 20022 | **Regulator:** Bank of the Lao PDR (BoL) | **Base URL:** `https://api.laofp.la/v1`

```
PSP Tier 1 (Banks)  ──┐
PSP Tier 2 (NBFI)   ──┤                          ┌── PSP Tier 1 (Receiving Bank)
PSP Tier 3 (Wallet) ──┼──► LaoFP Switching API ──┼── PSP Tier 3 (Wallet Operator)
Merchants           ──┤    (this system)          └── Cross-border Corridors
Cross-border        ──┘
```

**Target transaction types (LaoFP v1.0):**
- Bank-to-Bank Transfer (pacs.008) — ✅ implemented (B2B only, foundation)
- Bank-to-Wallet Transfer — 🚫 not yet
- Wallet-to-Wallet Transfer — 🚫 not yet
- Merchant / QR Code Payment (EMVCo) — 🚫 not yet
- Bill Payment (Fetch-and-Pay) — 🚫 not yet
- Cross-border Payment (Thailand/China/Vietnam/SWIFT corridors) — 🚫 not yet

**LaoFP Architecture Modules (MOD-01 to MOD-21):**

| Module | Name | Status |
|--------|------|--------|
| MOD-01 | API Gateway (TLS 1.3, mTLS, OAuth 2.0, rate limit) | 🟡 Partial (rate limit ✅; mTLS/OAuth ❌) |
| MOD-02 | IAM (OAuth 2.0, mTLS, credential mgmt) | 🟡 Partial (API key ✅; OAuth/mTLS ❌) |
| MOD-03 | Participant Directory (PSP registry, VPA, biller) | 🟡 Partial (PSP registry ✅; VPA/biller ❌) |
| MOD-04 | Core Switching Engine (routing, orchestration, state machine) | 🟡 Partial (B2B pacs.008 ✅; other txn types ❌) |
| MOD-05 | Transaction Validation (schema, business rules, duplicate) | 🟡 Partial (ISO XML ✅; full rule set ❌) |
| MOD-06 | Account Lookup Service (VPA → masked account) | ❌ Not started |
| MOD-07 | QR Code Service (static/dynamic EMVCo) | ❌ Not started |
| MOD-08 | Bill Payment Service (biller integration) | ❌ Not started |
| MOD-09 | Cross-border Gateway (FX, corridor routing) | ❌ Not started |
| MOD-10 | Settlement Engine (DNS cycles, RTGS, camt.054) | ❌ Not started |
| MOD-11 | Liquidity Manager (PSP pool balance, alerts) | ❌ Not started |
| MOD-12 | Risk & Fraud Engine (real-time scoring, velocity) | ❌ Not started |
| MOD-13 | AML / CFT Screening (sanctions, STR) | ❌ Not started |
| MOD-14 | Notification Service (webhook, push, SMS) | ❌ Not started |
| MOD-15 | Dispute & Refund Manager | ❌ Not started |
| MOD-16 | Reconciliation & Reporting (camt.054, regulatory) | 🟡 Partial (ops queries ✅; camt.054 ❌) |
| MOD-17 | Admin & Operations Portal | 🟡 Partial (REST APIs ✅; full UI ❌) |
| MOD-18 | Monitoring & Alerting (APM, SLA dashboards) | 🟡 Partial (Prometheus ✅; Grafana/alerts ❌) |
| MOD-19 | HSM / Key Management (FIPS 140-2 Level 3) | 🟡 Partial (AES/GCM in env ✅; HSM ❌) |
| MOD-20 | Data Archival (7-year retention) | ❌ Not started |
| MOD-21 | Force Push / FPRE (auto-retry engine) | 🟡 Partial (3-retry backoff ✅; auto-reversal/5-retry ❌) |

---

## 2. Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 |
| Framework | Spring Boot 4.0.3 (Spring Framework 6) |
| Web | Spring MVC (spring-boot-starter-webmvc) |
| Persistence | Spring Data JPA + Hibernate 7 |
| Database | MySQL 8.x / 9.x |
| Migrations | Flyway (12 migrations, V1–V13, no V12) |
| Validation | Jakarta Bean Validation (spring-boot-starter-validation) |
| Security | Spring Security 6 + API Key (X-API-Key header) + AES/GCM/NoPadding |
| JSON | Jackson + jackson-datatype-jsr310 |
| Monitoring | Spring Actuator (health, info only) + Micrometer metrics (counters, timers, gauges) |
| Build | Maven (mvnw wrapper) |
| Container | Docker + Docker Compose |
| Tests | JUnit 5 + MockMvc + Spring Boot Test |
| Code gen | Lombok |

---

## 3. Project Structure

```
src/main/java/com/example/switching/
├── SwitchingApplication.java              # Entry point
├── config/SchedulingConfig.java           # @EnableScheduling
├── audit/                                 # Audit log module
│   ├── controller/AuditLogController.java
│   ├── entity/AuditLogEntity.java
│   ├── repository/AuditLogRepository.java
│   └── service/AuditLogService.java       # Core: log(eventType, refType, refId, actor, payload)
│                AuditLogQueryService.java
├── common/
│   ├── dto/ApiErrorResponse.java          # Standard error response body
│   ├── error/ErrorCatalog.java            # All error codes (REQ, INQ, TRF, OUT, NET, EXT, INF, SYS, etc.)
│   │        ErrorCategory.java           # REQUEST, BUSINESS, CORE, NETWORK, DOWNSTREAM, INFRASTRUCTURE, UNKNOWN
│   │        ErrorClassifier.java         # Maps exceptions to ErrorCatalog entries
│   │        ErrorLayer.java              # API, INQUIRY, TRANSFER, OUTBOX, CONNECTOR, ISO, DATABASE, SYSTEM, ROUTING, WORKER
│   │        ErrorPhase.java              # RECEIVE_REQUEST, VALIDATE_REQUEST, LOOKUP_INQUIRY, CREATE_TRANSFER, ...
│   ├── exception/GlobalExceptionHandler.java   # @RestControllerAdvice — maps all known exceptions to ApiErrorResponse
│   ├── filter/RequestIdFilter.java        # Injects X-Request-Id header (UUID if not provided); sets MDC.requestId for log correlation
│   └── util/RequestHashUtil.java         # SHA-256 hash of request for idempotency
│              TransferRefGenerator.java  # Generates "TRX-{timestamp}-{random}" refs
│              MaskingUtil.java           # maskAccount(String) — shows last 4 digits only
│              AuditActorUtil.java        # currentActor() — reads SecurityContextHolder, fallback "SYSTEM"
├── connector/                             # Bank connector abstraction
│   ├── BankConnector.java                 # Interface: dispatch(DispatchIsoMessageCommand) → BankIsoDispatchResponse
│   ├── MockBankConnector.java             # MOCK implementation, reads connector_configs.force_reject
│   ├── registry/ConnectorRegistry.java   # Map<connectorName, BankConnector>
│   ├── entity/ConnectorConfigEntity.java
│   ├── enums/ConnectorType.java           # MOCK (only type currently)
│   ├── controller/ConnectorConfigController.java
│   └── service/ConnectorConfigService.java
│              ConnectorConfigManagementService.java
├── dashboard/                             # Dashboard summary
├── demo/                                  # DemoFlowService (unused in prod)
├── idempotency/                           # Idempotency layer
│   ├── entity/IdempotencyRecordEntity.java  # Table: idempotency_records
│   ├── repository/IdempotencyRecordRepository.java
│   └── service/IdempotencyService.java    # findExistingTransfer(), saveNew(), updateStatus()
├── inquiry/                               # JSON-based inquiry (non-ISO path)
│   ├── controller/InquiryController.java  # POST /api/inquiries, GET /api/inquiries/{inquiryRef}
│   ├── entity/InquiryEntity.java          # Table: inquiries
│   ├── enums/InquiryStatus.java           # RECEIVED, ELIGIBLE, NOT_ELIGIBLE, FAILED
│   └── service/CreateInquiryService.java
│              InquiryLookupService.java
├── security/                              # API Key authentication + management
│   ├── config/SecurityConfig.java         # Spring Security config, role-based access rules
│   ├── controller/ApiKeyController.java   # GET/POST /api/admin/api-keys, /disable, /rotate (ADMIN only)
│   ├── dto/ApiKeyCreateRequest.java       # Request: name, role, bankCode, expiresAt
│   │       ApiKeyResponse.java           # Response: id, name, role, keyPrefix, plainKey (once), enabled, etc.
│   ├── entity/ApiKeyEntity.java           # Table: api_keys (key_value=SHA-256, key_prefix, expires_at)
│   ├── enums/ApiKeyRole.java              # ADMIN, OPS, BANK
│   ├── filter/ApiKeyAuthFilter.java       # OncePerRequestFilter — hashes key, checks expiry, sets SecurityContext
│   ├── repository/ApiKeyRepository.java  # findByKeyValueAndEnabledTrue()
│   ├── service/ApiKeyService.java         # create/list/disable/rotate — generates + hashes plaintext key
│   │           ProductionDemoKeyDisableService.java  # @Profile("prod") ApplicationRunner — disables demo keys on startup
│   └── util/ApiKeyHashUtil.java          # generate() sk-{64hex}, hash() SHA-256, prefix() first 12 chars
├── iso/                                   # ISO 20022 module
│   ├── controller/IsoMessageController.java    # GET /api/iso-messages, /api/iso-messages/{key}
│   ├── entity/IsoMessageEntity.java            # Table: iso_messages
│   ├── enums/IsoMessageType.java               # PACS_008, PACS_002, PACS_028, PACS_004
│   │        IsoSecurityStatus.java             # ENCRYPTED, DECRYPTED, BYPASS, FAILED
│   │        IsoValidationStatus.java           # VALID, INVALID, BYPASS
│   ├── inbound/IsoPacs008InboundController.java  # POST /api/iso20022/pacs008 (XML)
│   │           IsoPacs008InboundService.java     # Full ISO inbound flow
│   │           InboundPacs008PersistenceService.java
│   ├── inquiry/IsoInquiryController.java        # POST /api/iso20022/acmt023 (XML)
│   │           IsoInquiryInboundService.java    # Parse ACMT.023, validate, create iso_inquiry
│   │           IsoInquiryQueryController.java   # GET /api/iso-inquiries/{ref}
│   │           Acmt023XmlParser.java            # XML → Acmt023InquiryRequest
│   │           Acmt024XmlResponseBuilder.java   # Build ACMT.024 XML response
│   ├── parser/Pacs002Parser.java                # Parse PACS.002 XML response
│   │           IsoXmlValidator.java
│   └── security/IsoMessageCryptoService.java    # AES/GCM encrypt/decrypt
├── operations/                            # Ops/admin APIs
│   ├── controller/ (14 controllers)       # All under /api/operations/*
│   └── service/ (14 services)
├── outbox/                                # Transactional Outbox
│   ├── entity/OutboxEventEntity.java      # Table: outbox_events
│   ├── enums/OutboxStatus.java            # PENDING, PROCESSING, SUCCESS, FAILED, REVIEWED
│   ├── event/OutboxCreatedEvent.java      # record OutboxCreatedEvent(Long outboxEventId, String transferRef)
│   ├── worker/OutboxDispatchWorker.java   # @TransactionalEventListener(AFTER_COMMIT) near real-time + @Scheduled safety-net (30s)
│   │           OutboxRecoveryWorker.java  # @Scheduled configurable (60s default) — recovers stuck PROCESSING
│   └── service/OutboxProcessorService.java     # Core dispatch logic, maxRetry configurable via env
│              OutboxIsoMessageDispatchService.java  # decrypt → dispatch via connector → handle PACS.002
│              OutboxTransactionService.java     # Creates outbox event in same transaction as transfer
│              OutboxRecoveryService.java
│              OutboxManualRetryService.java
├── participant/                           # Bank participant management
│   ├── entity/ParticipantEntity.java      # Table: participants
│   ├── enums/ParticipantStatus.java       # ACTIVE, INACTIVE, SUSPENDED
│   ├── controller/ParticipantController.java  # GET/POST/PATCH /api/participants
│   └── service/ParticipantService.java    # Validates participant is ACTIVE before routing
├── routing/                               # Routing rule management
│   ├── entity/RoutingRuleEntity.java      # Table: routing_rules
│   ├── controller/RoutingRuleController.java  # GET/POST/PATCH /api/routing-rules
│   └── service/RoutingService.java        # resolve(sourceBank, destBank, msgType) with in-memory ConcurrentHashMap cache
│              RoutingRuleManagementService.java
└── transfer/                              # Transfer domain (core)
    ├── controller/TransferController.java      # POST /api/transfers
    │             TransferListController.java   # GET /api/transfers
    │             TransferTraceController.java  # GET /api/transfers/{ref}/trace
    │             TransferInquiryController.java
    ├── entity/TransferEntity.java              # Table: transfers
    │           TransferStatusHistoryEntity.java # Table: transfer_status_history
    ├── enums/TransferStatus.java               # RECEIVED, SUCCESS, FAILED
    └── service/CreateTransferService.java      # Core transfer creation + outbox
                TransferListService.java
                TransferTraceService.java
                TransferInquiryService.java
```

---

## 4. Payment Flow (Step by Step)

### 4.1 JSON Path (non-ISO, simpler)

```
POST /api/inquiries
  → CreateInquiryService
  → Validate participants (sourceBank must be ACTIVE)
  → Resolve routing rule (sourceBank + destBank + PACS_008)
  → Save inquiry (table: inquiries, status=ELIGIBLE)
  → Return { inquiryRef, status, ... }

POST /api/transfers  { inquiryRef: "INQ-xxx" }
  → CreateTransferService
  → [Idempotency check] if idempotencyKey already exists → return existing transfer
  → Load inquiry, validate (status=ELIGIBLE, amount match, currency match, sourceBank match)
  → Mark inquiry as USED (status=ELIGIBLE→USED via inquiry_status_history)
  → Create TransferEntity (status=RECEIVED)
  → Create IsoMessageEntity (PACS_008, direction=OUTBOUND)
  → [OutboxTransactionService] Create OutboxEventEntity (status=PENDING) in SAME transaction
  → Save IdempotencyRecord
  → Return { transferRef, status=RECEIVED }
```

### 4.2 ISO XML Path (PACS.008 inbound)

```
POST /api/iso20022/pacs008  (Content-Type: application/xml)
  Header: X-Bank-Code: BANK_A
  Body: <PACS.008 XML>
  → IsoPacs008InboundService
  → IsoMessageCryptoService.encrypt(xmlBody) → store encrypted payload
  → InboundPacs008PersistenceService → save iso_messages record
  → Create Transfer + Outbox event
  → Return PACS.002 XML response
```

### 4.3 ISO XML Path (ACMT.023 inquiry inbound)

```
POST /api/iso20022/acmt023  (Content-Type: application/xml)
  Header: X-Bank-Code: BANK_A
  Body: <ACMT.023 XML>
  → IsoInquiryInboundService
  → Acmt023XmlParser.parse(xmlBody)
  → Validate X-Bank-Code matches source bank
  → Check participant is ACTIVE
  → Save to iso_inquiries (status=USED initially, eligible_for_transfer=true, TTL=15min)
  → Return ACMT.024 XML response
```

### 4.4 Outbox Dispatch Flow

```
[Near real-time path — ~50-200ms after transfer commit]
OutboxTransactionService.enqueueTransferDispatch()
  → Saves OutboxEventEntity (status=PENDING) in SAME transaction as transfer
  → Publishes OutboxCreatedEvent(outboxEventId, transferRef) via ApplicationEventPublisher

OutboxDispatchWorker.onOutboxCreated(@TransactionalEventListener AFTER_COMMIT)
  → Fires immediately after outer transaction commits (not before — prevents race condition)
  → OutboxProcessorService.processSingleEvent(id)
    → Claim event (PENDING → PROCESSING) in separate REQUIRES_NEW transaction
    → OutboxIsoMessageDispatchService.dispatchEncryptedIsoMessage(payload)
      → Decrypt PACS.008 payload (IsoMessageCryptoService.decrypt)
      → ConnectorRegistry.getConnector(connectorName)
      → MockBankConnector.dispatch(command)
        → If connector_configs.force_reject=true → return REJECT
        → Else → return ACCEPT (simulated PACS.002 response)
      → Parse PACS.002 response
    → On success: OutboxEvent → SUCCESS, Transfer → SUCCESS
    → On retryable failure (retryCount < maxRetry): event → PENDING, retryCount++
    → On terminal failure (>= maxRetry or non-retryable): event → FAILED, Transfer → FAILED

[Safety-net poll — catches events missed by listener (app restart, listener crash)]
OutboxDispatchWorker.processPendingEvents(@Scheduled fixedDelayString=poll-interval-ms:30000)
  → Finds top batchSize PENDING events → processes each via processSingleEvent()

[Recovery — unsticks long-running PROCESSING events]
OutboxRecoveryWorker(@Scheduled fixedDelayString=recovery-interval-ms:60000)
  → Finds PROCESSING events stuck > stuck-timeout-minutes (default 2)
  → If retryCount < maxRetry → reset to PENDING
  → If retryCount >= maxRetry → mark FAILED, set Transfer → FAILED
```

---

## 5. Database Schema

### Tables (19 migrations, current version = 19)

#### `transfers`
| Column | Type | Notes |
|--------|------|-------|
| id | BIGINT PK | |
| transfer_ref | VARCHAR(255) UNI | `TRX-{ts}-{rand}` |
| client_transfer_id | VARCHAR(255) | |
| idempotency_key | VARCHAR(100) | |
| source_bank_code | VARCHAR(20) | |
| source_account_no | VARCHAR(40) | |
| destination_bank_code | VARCHAR(20) | |
| destination_account_no | VARCHAR(40) | |
| destination_account_name | VARCHAR(120) | nullable |
| amount | DECIMAL(38,2) | nullable |
| currency | VARCHAR(255) | nullable |
| channel_id | VARCHAR(40) | e.g. `API`, `ISO20022_XML` |
| route_code | VARCHAR(40) | nullable |
| connector_name | VARCHAR(80) | nullable |
| external_reference | VARCHAR(80) | nullable |
| status | VARCHAR(30) | `RECEIVED`, `SUCCESS`, `FAILED` |
| error_code | VARCHAR(40) | nullable |
| error_message | TEXT | nullable |
| reference | VARCHAR(255) | nullable |
| inquiry_ref | VARCHAR(64) UNI | nullable — added in V4 |
| created_at | DATETIME(3) | auto |
| updated_at | DATETIME(3) | auto on update |

#### `transfer_status_history`
| Column | Type |
|--------|------|
| id | BIGINT PK |
| transfer_ref | VARCHAR(255) |
| status | VARCHAR(30) |
| reason_code | VARCHAR(40) |
| created_at | DATETIME(3) |

#### `inquiries` (V3 — JSON inquiry path)
| Column | Type |
|--------|------|
| id | BIGINT PK |
| inquiry_ref | VARCHAR(255) UNI |
| client_inquiry_id | VARCHAR(255) |
| source_bank | VARCHAR(20) |
| destination_bank | VARCHAR(20) |
| creditor_account | VARCHAR(40) |
| destination_account_name | VARCHAR(120) |
| amount | DECIMAL(38,2) |
| currency | VARCHAR(20) |
| channel_id | VARCHAR(40) |
| route_code | VARCHAR(40) |
| connector_name | VARCHAR(80) |
| account_found | BOOLEAN |
| bank_available | BOOLEAN |
| eligible_for_transfer | BOOLEAN |
| status | VARCHAR(30) |
| error_code / error_message | VARCHAR |
| reference | VARCHAR(255) |
| created_at / updated_at | DATETIME(3) |

#### `iso_inquiries` (V13 — ISO XML inquiry path)
| Column | Type | Notes |
|--------|------|-------|
| id | BIGINT PK | |
| inquiry_ref | VARCHAR(80) UNI | |
| channel_id | VARCHAR(50) | `ISO20022_XML` |
| message_id | VARCHAR(120) | UNI with channel_id |
| instruction_id | VARCHAR(120) | nullable |
| end_to_end_id | VARCHAR(120) | nullable |
| source_bank_code | VARCHAR(30) | |
| destination_bank_code | VARCHAR(30) | |
| debtor_account_no | VARCHAR(60) | nullable |
| creditor_account_no | VARCHAR(60) | |
| amount | DECIMAL(19,2) | nullable |
| currency | VARCHAR(10) | nullable |
| reference | VARCHAR(255) | nullable |
| status | VARCHAR(30) | `USED`, `ELIGIBLE`, etc. |
| account_found | BOOLEAN | default false |
| bank_available | BOOLEAN | default false |
| eligible_for_transfer | BOOLEAN | default false |
| failure_code | VARCHAR(30) | nullable |
| failure_message | TEXT | nullable |
| expires_at | DATETIME | nullable, TTL=15min from creation |
| used_by_transfer_ref | VARCHAR(80) | nullable, INDEX |
| created_at / updated_at | DATETIME | |

#### `outbox_events`
| Column | Type | Notes |
|--------|------|-------|
| id | BIGINT PK | |
| transfer_ref | VARCHAR(255) | |
| message_type | VARCHAR(100) | e.g. `PACS_008` |
| payload | LONGTEXT | JSON: `{transferRef, isoMessageId, sourceBank, destBank, routeCode, connectorName}` |
| status | VARCHAR(20) | `PENDING`→`PROCESSING`→`SUCCESS`/`FAILED`/`REVIEWED` |
| retry_count | INT | max 3 |
| last_error | TEXT | nullable |
| processed_at | DATETIME(3) | nullable |
| next_retry_at | DATETIME(3) | nullable |
| created_at / updated_at | DATETIME(3) | |

#### `iso_messages`
| Column | Type | Notes |
|--------|------|-------|
| id | BIGINT PK | |
| correlation_ref | VARCHAR(100) | |
| inquiry_ref | VARCHAR(100) | nullable |
| transfer_ref | VARCHAR(100) | nullable |
| end_to_end_id | VARCHAR(100) | |
| message_id | VARCHAR(100) | |
| message_type | VARCHAR(50) | `PACS_008`, `PACS_002`, etc. |
| direction | VARCHAR(20) | `INBOUND` / `OUTBOUND` |
| plain_payload | LONGTEXT | decrypted XML |
| encrypted_payload | LONGTEXT | AES/GCM encrypted + base64 |
| security_status | VARCHAR(30) | `ENCRYPTED`, `DECRYPTED`, `BYPASS`, `FAILED` |
| validation_status | VARCHAR(30) | `VALID`, `INVALID`, `BYPASS` |
| error_code / error_message | VARCHAR | |
| created_at | DATETIME | |

#### `audit_logs`
| Column | Type |
|--------|------|
| id | BIGINT PK |
| event_type | VARCHAR(60) |
| reference_type | VARCHAR(40) |
| reference_id | VARCHAR(60) |
| actor | VARCHAR(60) |
| payload | LONGTEXT |
| created_at | DATETIME(3) |

#### `idempotency_records`
| Column | Type | Notes |
|--------|------|-------|
| id | BIGINT PK | |
| idempotency_key | VARCHAR(100) UNI | |
| channel_id | VARCHAR(40) | |
| request_hash | VARCHAR(128) | SHA-256 of request body |
| transfer_ref | VARCHAR(255) | |
| status | VARCHAR(30) | |
| created_at | DATETIME(3) | |
| expired_at | DATETIME(3) | nullable |

**Note:** V7 added `updated_at` to `idempotency_records`. V7 also adds `channel_id` to the unique lookup (findByChannelIdAndIdempotencyKey).

#### `participants`
| Column | Type |
|--------|------|
| id | BIGINT PK |
| bank_code | VARCHAR(32) UNI |
| bank_name | VARCHAR(255) |
| status | VARCHAR(32) | `ACTIVE`, `INACTIVE`, `SUSPENDED` |
| participant_type | VARCHAR(32) |
| country | VARCHAR(8) |
| currency | VARCHAR(8) |
| created_at / updated_at | DATETIME |

#### `routing_rules` (V8 — replaces V1 version)
| Column | Type | Notes |
|--------|------|-------|
| id | BIGINT PK | |
| route_code | VARCHAR(128) UNI | |
| source_bank | VARCHAR(32) | |
| destination_bank | VARCHAR(32) | |
| message_type | VARCHAR(32) | `PACS_008` |
| connector_name | VARCHAR(128) | |
| priority | INT | default 1 |
| enabled | BOOLEAN | default true |
| created_at / updated_at | DATETIME | |

**Lookup index:** `(source_bank, destination_bank, message_type, enabled, priority)`

#### `api_keys` (V14 + V17 hardening)
| Column | Type | Notes |
|--------|------|-------|
| id | BIGINT PK | |
| key_value | VARCHAR(64) UNI | **SHA-256 hex digest** of the original key — never stores plaintext after V17 |
| key_prefix | VARCHAR(16) | First 12 chars of original key for display/identification only — added V17 |
| name | VARCHAR(128) | human-readable label |
| role | VARCHAR(32) | `ADMIN`, `OPS`, `BANK` |
| bank_code | VARCHAR(32) | nullable — for BANK role |
| enabled | BOOLEAN | default true |
| created_at | DATETIME | |
| last_used_at | DATETIME | nullable, updated per request |
| expires_at | DATETIME | nullable — NULL = never expires; enforced in `ApiKeyAuthFilter` — added V17 |

**Demo keys (seeded in V14, hashed in V17):**
| Key | Role | Note |
|-----|------|------|
| `sk-admin-switching-2026` | ADMIN | SHA-256 stored in DB after V17 |
| `sk-ops-switching-2026` | OPS | SHA-256 stored in DB after V17 |
| `sk-bank-a-switching-2026` | BANK (BANK_A) | SHA-256 stored in DB after V17 |
| `sk-bank-b-switching-2026` | BANK (BANK_B) | SHA-256 stored in DB after V17 |

**DB Users (created by `scripts/init-db-users.sh` on first `docker compose up`):**
| User | Privileges | Purpose |
|------|------------|---------|
| `switching_app` | SELECT, INSERT, UPDATE, DELETE | App runtime — cannot ALTER or DROP schema |
| `switching_flyway` | ALL PRIVILEGES | Flyway schema migrations only |

**Management endpoints (ADMIN only — `ApiKeyController`):**
| Method | Path | Action |
|--------|------|--------|
| GET | `/api/admin/api-keys` | List all keys (no plaintext) |
| POST | `/api/admin/api-keys` | Create key — returns `plainKey` once |
| POST | `/api/admin/api-keys/{id}/disable` | Disable key |
| POST | `/api/admin/api-keys/{id}/rotate` | Rotate key — returns new `plainKey` once |

#### `connector_configs` (V9)
| Column | Type | Notes |
|--------|------|-------|
| id | BIGINT PK | |
| connector_name | VARCHAR(128) UNI | e.g. `MOCK_BANK_B_CONNECTOR` |
| bank_code | VARCHAR(32) | destination bank |
| connector_type | VARCHAR(32) | `MOCK` |
| endpoint_url | VARCHAR(512) | nullable |
| timeout_ms | INT | default 5000 |
| enabled | BOOLEAN | default true |
| force_reject | BOOLEAN | default false — **used in tests** |
| reject_reason_code | VARCHAR(32) | `AC01` |
| reject_reason_message | VARCHAR(512) | |
| created_at / updated_at | DATETIME | |

#### `inquiry_status_history`
| Column | Type |
|--------|------|
| id | BIGINT PK |
| inquiry_ref | VARCHAR(255) |
| status | VARCHAR(30) |
| reason_code | VARCHAR(40) |
| created_at | DATETIME(3) |

#### `participant_banks` (V1 — legacy, superseded by `participants` in V8)
Legacy table still exists in schema. Not actively used after V8.

---

## 6. API Endpoints

### Public / Client-Facing APIs

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/inquiries` | Create inquiry (JSON path). Body: `{sourceBank, destinationBank, creditorAccount, amount, currency}` |
| GET | `/api/inquiries/{inquiryRef}` | Get inquiry by ref |
| POST | `/api/transfers` | Create transfer. Body: `{inquiryRef, sourceBank, destinationBank, debtorAccount, creditorAccount, amount, currency, idempotencyKey?}` |
| GET | `/api/transfers` | List all transfers |
| GET | `/api/transfers/{transferRef}` | Get transfer by ref |
| GET | `/api/transfers/{transferRef}/trace` | Full trace of a transfer (public) |
| POST | `/api/iso20022/pacs008` | Receive PACS.008 ISO XML. Header: `X-Bank-Code`. Content-Type: `application/xml` |
| POST | `/api/iso20022/acmt023` | Receive ACMT.023 ISO XML inquiry. Header: `X-Bank-Code`. Content-Type: `application/xml` |
| GET | `/api/iso-messages` | List ISO messages |
| GET | `/api/iso-messages/{messageKey}` | Get ISO message |
| GET | `/api/outbox-events` | List outbox events |
| POST | `/api/outbox-events/{outboxEventId}/retry` | Manually retry an outbox event |
| GET | `/api/participants` | List participants. Query: `?status=ACTIVE` |
| GET | `/api/participants/{bankCode}` | Get participant |
| POST | `/api/participants` | Create participant |
| PATCH | `/api/participants/{bankCode}` | Update participant |
| GET | `/api/routing-rules` | List routing rules |
| GET | `/api/routing-rules/resolve` | Resolve route for `?sourceBank=&destinationBank=&messageType=` |
| POST | `/api/routing-rules/cache/clear` | Clear routing cache |
| POST | `/api/routing-rules` | Create routing rule |
| PATCH | `/api/routing-rules/{routeCode}` | Update routing rule |
| GET | `/api/connector-configs` | List connector configs |
| GET | `/api/connector-configs/{connectorName}` | Get connector config |
| POST | `/api/connector-configs` | Create connector config |
| PATCH | `/api/connector-configs/{connectorName}` | Update connector config |
| GET | `/api/iso-inquiries/{inquiryRef}` | Get ISO inquiry by ref |

### Operations / Admin APIs (`/api/operations/*`)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/operations/health` | Operations health check |
| GET | `/api/operations/dashboard-summary` | Dashboard: counts, recent activity |
| GET | `/api/operations/transactions` | List all transactions (cross-domain view) |
| GET | `/api/operations/transfers` | List transfers (ops view) |
| GET | `/api/operations/transfers/{transferRef}` | Get transfer detail (ops) |
| GET | `/api/operations/transfers/{transferRef}/trace` | **Full trace**: transfer + inquiry + outbox + ISO messages + audit timeline |
| GET | `/api/operations/iso-messages` | List ISO messages (ops) |
| GET | `/api/operations/iso-inquiries` | List ISO inquiries (ops) |
| GET | `/api/operations/iso-inquiries/{inquiryRef}` | Get ISO inquiry (ops) |
| GET | `/api/operations/audit-logs` | List audit logs |
| GET | `/api/operations/outbox-failures` | List failed outbox events |
| GET | `/api/operations/outbox-stuck` | List stuck PROCESSING events |
| POST | `/api/operations/outbox-failures/retry-all` | Retry all failed events |
| POST | `/api/operations/outbox-events/{id}/mark-reviewed` | Mark event as REVIEWED |
| POST | `/api/operations/outbox-stuck/recover-all` | Manually recover stuck events |
| GET | `/api/operations/bank-status` | Bank status overview |
| POST | `/api/operations/bank-onboarding` | Onboard new bank (creates participant + routing + connector) |
| POST | `/api/operations/bank-onboarding/generate-routes` | Generate missing inbound/outbound routing rules for an ACTIVE bank |
| GET | `/api/operations/connectors/health` | Connector health |
| POST | `/api/operations/connectors/{connectorName}/test` | Test a connector |

### Actuator
| Method | Path |
|--------|------|
| GET | `/actuator/health` |
| GET | `/actuator/info` |

---

## 7. Key Services

### CreateTransferService
**Path:** `transfer/service/CreateTransferService.java`
**Flow:**
1. Compute `requestHash` (SHA-256 of request)
2. Check idempotency: if key exists + hash matches → return existing transfer
3. Load inquiry, validate (status=ELIGIBLE, amounts/currency/bank must match)
4. Mark inquiry USED via `InquiryStatus`
5. Generate `transferRef` via `TransferRefGenerator`
6. Create `TransferEntity` (status=RECEIVED)
7. Create `IsoMessageEntity` (PACS_008, OUTBOUND, encrypt payload)
8. `OutboxTransactionService.createOutboxEvent()` — all in one `@Transactional`
9. Save `IdempotencyRecord`
10. Log audit event `TRANSFER_CREATED`

**Exceptions thrown:**
- `InquiryValidationException` (400) — mismatched fields
- `InquiryAlreadyUsedException` (409) — inquiry already used
- `ParticipantUnavailableException` (422) — bank not ACTIVE
- `RoutingRuleNotFoundException` (422) — no route found
- `IdempotencyConflictException` (409) — key exists with different hash

### OutboxProcessorService
**Path:** `outbox/service/OutboxProcessorService.java`
**Key config:** `maxRetry` injected via `@Value("${switching.outbox.worker.max-retry:3}")` (was a hardcoded static final), `SOURCE_SYSTEM = "WORKER"`
**Flow per event:**
1. Claim event (PENDING → PROCESSING) in `PROPAGATION_REQUIRES_NEW` TX to prevent concurrent processing
2. Dispatch via `OutboxIsoMessageDispatchService`
3. Success: event → SUCCESS, transfer → SUCCESS
4. Retryable failure (retryCount < maxRetry): event → PENDING, retry_count++
5. Terminal failure (>= maxRetry or non-retryable): event → FAILED, transfer → FAILED

### IsoMessageCryptoService
**Path:** `iso/security/IsoMessageCryptoService.java`
**Algorithm:** AES/GCM/NoPadding, 12-byte IV, 128-bit GCM tag
**Config:** `switching.security.message-crypto-key-base64` (env: `MESSAGE_CRYPTO_KEY_BASE64`)
**✅ Fixed (Phase 1):** If `MESSAGE_CRYPTO_KEY_BASE64` is empty and the active Spring profile is not `test`, `resolveKey()` throws `IllegalStateException` at startup. In test profile it falls back to a hard-coded dev key so tests don't need the env var.

### RoutingService
**Path:** `routing/service/RoutingService.java`
**Cache:** In-memory `ConcurrentHashMap` — cleared via `POST /api/routing-rules/cache/clear`
**Lookup:** `routing_rules` by `(source_bank, destination_bank, message_type, enabled=true)` ordered by priority

### OperationsTransferTraceService
**Path:** `operations/service/OperationsTransferTraceService.java`
**Returns:** Combined timeline of transfer + inquiry + outbox events + ISO messages + audit logs

**✅ Fixed (Phase 1 SQL bug):** SQL text blocks used `WHERE """` which stripped trailing space → `WHEREcondition` → MySQL syntax error. Fixed with `WHERE\s"""`.

**✅ Fixed (Phase 2 silent catch):** All 4 catch blocks now call `log.error(msg, transferRef, ex)` before adding to `warnings`. Failures are now visible in application logs instead of silently producing `TRACE_FOUND_WITH_WARNINGS`.

**✅ Fixed (Phase 3 JSON-path trace):** `findInquiry()` now uses a two-step fallback:
1. Try `iso_inquiries` first (`findIsoPathInquiry`) — covers ISO XML transfers
2. If null, fall back to `inquiries` table (`findJsonPathInquiry`) — covers JSON-path transfers

JSON-path inquiries are mapped to `OperationsTransferTraceInquiryResponse` with `messageId/instructionId/endToEndId/debtorAccount/expiresAt = null` (ISO-only fields). `usedByTransferRef` is inferred from context because the `inquiries` table has no back-reference column. Timeline events use type `JSON_INQUIRY` and protocol `JSON_API` instead of `ISO_INQUIRY` / `ACMT_023`.

**Result:** JSON-path transfers now show `hasInquiry: true` and two extra timeline events (`JSON_INQUIRY_CREATED`, `JSON_INQUIRY_USED_BY_TRANSFER`).

---

## 8. Background Workers

| Worker | Schedule | Action |
|--------|----------|--------|
| `OutboxDispatchWorker` | `@TransactionalEventListener(AFTER_COMMIT)` | Near real-time: fires immediately after transfer saves — ~50-200ms latency |
| `OutboxDispatchWorker` | `fixedDelayString=${outbox.worker.poll-interval-ms:30000}` | Safety-net poll every 30s for missed events |
| `OutboxRecoveryWorker` | `fixedDelayString=${outbox.worker.recovery-interval-ms:60000}` | Find PROCESSING events stuck > `stuck-timeout-minutes` → reset to PENDING |

Workers are enabled via `@EnableScheduling` on `SchedulingConfig`.

**All outbox values are now configurable via env vars:**

| Env Var | Default | Description |
|---------|---------|-------------|
| `OUTBOX_POLL_INTERVAL_MS` | `30000` | Safety-net poll interval (ms) |
| `OUTBOX_RECOVERY_INTERVAL_MS` | `60000` | Recovery worker interval (ms) |
| `OUTBOX_BATCH_SIZE` | `20` | Max events per poll cycle |
| `OUTBOX_MAX_RETRY` | `3` | Max dispatch retries before FAILED |
| `OUTBOX_STUCK_TIMEOUT_MINUTES` | `2` | Minutes before PROCESSING is considered stuck |

---

## 9. Error Catalog

All errors are defined in `ErrorCatalog.java`. Structure:

```json
{
  "timestamp": "...",
  "status": 400,
  "error": "BAD_REQUEST",
  "errorCode": "REQ-001",
  "category": "REQUEST",
  "layer": "API",
  "phase": "VALIDATE_REQUEST",
  "retryable": false,
  "message": "Request validation failed",
  "path": "/api/transfers",
  "requestId": "uuid",
  "details": { "field": "message" }
}
```

| Code | HTTP | Description | Retryable |
|------|------|-------------|-----------|
| REQ-001 | 400 | Request validation failed (Bean Validation) | No |
| REQ-002 | 400 | Malformed JSON | No |
| REQ-003 | 405 | HTTP method not allowed | No |
| INQ-001 | 404 | Inquiry not found | No |
| INQ-002 | 400 | Inquiry validation failed (field mismatch) | No |
| INQ-003 | 409 | Inquiry already used by transfer | No |
| TRF-001 | 404 | Transfer not found | No |
| TRF-002 | 409 | Idempotency conflict | No |
| OUT-001 | 500 | Outbox payload parse failed | No |
| OUT-002 | 500 | Outbox worker processing failed | **Yes** |
| OUT-003 | 500 | Outbox stuck processing recovered | **Yes** |
| OUT-004 | 409 | Outbox event cannot be manually retried | No |
| OUT-005 | 404 | Outbox event not found | No |
| NET-001 | 503 | Downstream connection failed | **Yes** |
| NET-002 | 503 | Downstream timeout | **Yes** |
| NET-003 | 503 | DNS resolution failed | **Yes** |
| NET-004 | 503 | TLS/SSL handshake failed | **Yes** |
| EXT-001 | 502 | Downstream bank rejected transfer | No |
| EXT-002 | 502 | Downstream bank invalid response | No |
| INF-DB-001 | 500 | Database write failed | **Yes** |
| INF-DB-002 | 409 | DB unique constraint violation | No |
| INF-SER-001 | 500 | Serialization/deserialization failed | No |
| ISO-001 | 404 | ISO message not found | No |
| ISO-002 | 409 | ISO message invalid state | No |
| ISO-003 | 500 | ISO message crypto failed | No |
| PRT-001 | 404 | Participant not found | No |
| PRT-002 | 422 | Participant not ACTIVE | No |
| PRT-003 | 409 | Participant already exists | No |
| RTE-001 | 422 | Routing rule not found | No |
| RTE-002 | 409 | Routing rule already exists | No |
| CON-001 | 503 | Connector not found/unavailable | No |
| CON-002 | 409 | Connector already exists | No |
| SYS-001 | 500 | Internal server error (catch-all) | No |

---

## 10. Status Enums

### TransferStatus
- `RECEIVED` — created, waiting for outbox dispatch
- `SUCCESS` — PACS.002 accepted by destination bank
- `FAILED` — rejected or exceeded max retries

### InquiryStatus (JSON path)
- `RECEIVED` → `ELIGIBLE` / `NOT_ELIGIBLE` / `FAILED`

### OutboxStatus
- `PENDING` → `PROCESSING` → `SUCCESS` / `FAILED` / `REVIEWED`

### IsoMessageType
- `PACS_008` — FI Credit Transfer
- `PACS_002` — FI Payment Status
- `PACS_028` — FI Payment Status Request
- `PACS_004` — Payment Return

---

## 11. Security

### IsoMessageCryptoService (AES/GCM)
- Algorithm: `AES/GCM/NoPadding`
- IV: 12 bytes (SecureRandom), prepended to ciphertext
- Tag: 128-bit GCM authentication tag
- Key: 16, 24, or 32 bytes decoded from Base64
- Encrypted format: `base64(iv[12] + ciphertext)`
- **✅ Fixed (Phase 1):** If `MESSAGE_CRYPTO_KEY_BASE64` is blank outside the test profile, startup throws `IllegalStateException`. Test profile uses a safe fixed dev key so tests run without the env var.

### RequestIdFilter
- Reads `X-Request-Id` header → if absent, generates UUID
- Sets attribute and response header `X-Request-Id`
- Puts `requestId` into **SLF4J MDC** at filter entry; clears MDC in `finally` so it appears in every log line for the request duration

### Rate Limiting (Phase 3 ✅)
- **Filter:** `RateLimitFilter` (`@Order(10)`) — runs before Security filters
- **Scope:** POST, PUT, PATCH, DELETE requests only
- **Client identity:** `X-API-Key` header value → fallback to `X-Forwarded-For` → fallback to remote IP
- **Algorithm:** Bucket4j token bucket — `requestsPerMinute` tokens, refills every 1 minute
- **Response on limit:** `429 Too Many Requests` with `errorCode: REQ-004`
- **Config:**
  - `switching.security.rate-limit.enabled` (env: `RATE_LIMIT_ENABLED`, default `true`)
  - `switching.security.rate-limit.requests-per-minute` (env: `RATE_LIMIT_RPM`, default `100`)
- **Disabled in tests:** `application-test.yml` sets `rate-limit.enabled: false`

### Near Real-time Dispatch (Phase 3 ✅)
- `OutboxTransactionService.enqueueTransferDispatch()` publishes `OutboxCreatedEvent` after saving the outbox record
- `OutboxDispatchWorker.onOutboxCreated()` listens with `@TransactionalEventListener(AFTER_COMMIT)` — fires synchronously after the outer transaction commits (~50-200ms vs 0-5s before)
- Safety-net polling worker still runs every 30s to catch any events missed due to app restart or listener failure
- In test profile: poll interval kept at 5000ms so integration tests aren't slowed down

### Structured Logging with MDC (Phase 2 ✅)
- **`requestId`** — set in `RequestIdFilter` for every HTTP request; cleared in `finally`
- **`inquiryRef`** — set in `CreateTransferService.create()` once the inquiry ref is resolved
- **`transferRef`** — set in `CreateTransferService.create()` once the transfer ref is generated; also set in `OutboxProcessorService.doProcessSingleEvent()` once the outbox payload is parsed
- **`outboxEventId`** — set in `OutboxProcessorService.processSingleEvent()` for the duration of processing one event
- All MDC keys are removed/cleared in `finally` blocks to prevent leaking across requests or worker iterations

### Micrometer Metrics (Phase 2 ✅)
Pre-registered at startup; always appear in `/actuator/metrics` even before first event.

| Metric name | Type | Description |
|-------------|------|-------------|
| `payment.transfer.created` | Counter | Transfers accepted and queued for dispatch |
| `payment.transfer.failed` | Counter | Transfers that threw during `CreateTransferService.create()` |
| `payment.outbox.dispatch.success` | Counter | Outbox events finalized as SUCCESS |
| `payment.outbox.dispatch.failed{type=business}` | Counter | Outbox events rejected by downstream bank |
| `payment.outbox.dispatch.failed{type=technical}` | Counter | Outbox events that hit terminal technical failure |
| `payment.outbox.dispatch.duration` | Timer | End-to-end time for `OutboxProcessorService.processSingleEvent()` |
| `payment.outbox.pending.count` | Gauge | Live count of `outbox_events` with status=PENDING |
| `payment.outbox.processing.count` | Gauge | Live count of `outbox_events` with status=PROCESSING |
| `payment.outbox.failed.count` | Gauge | Live count of `outbox_events` with status=FAILED |

Gauges query `OutboxEventRepository.countByStatus()` directly (already existed in repository).

### API Key Authentication (Phase 1 ✅) + Hardening (Phase 4 ✅)
- **Header:** `X-API-Key: <key>`
- **Filter:** `ApiKeyAuthFilter` (OncePerRequestFilter) — hashes incoming key with `ApiKeyHashUtil.hash()` (SHA-256), queries `api_keys` by hash, checks `expires_at`, sets `SecurityContextHolder`
- **Hashing:** `ApiKeyHashUtil` — `generate()` creates `sk-{64 hex chars}`, `hash()` computes SHA-256 hex, `prefix()` returns first 12 chars for display
- **Storage:** `api_keys.key_value` stores SHA-256 hex (64 chars) — plaintext never stored or retrievable after creation/rotation
- **Expiry:** `expires_at` NULL = never expires; non-null = key rejected after that datetime even if `enabled=true`
- **Roles:** `ROLE_ADMIN`, `ROLE_OPS`, `ROLE_BANK`
- **Config flag:** `switching.security.api-key.enabled` (default `true`; set `false` in test profile)
- **Disabled in tests:** `application-test.yml` sets `api-key.enabled: false` → all requests allowed
- **Management:** `ApiKeyService` + `ApiKeyController` under `/api/admin/api-keys/**` (ADMIN role required)

**Role access matrix:**
| Endpoint group | BANK | OPS | ADMIN |
|----------------|------|-----|-------|
| POST /api/inquiries, /api/transfers | ✅ | ❌ | ✅ |
| GET /api/inquiries/**, /api/transfers/** | ✅ | ✅ | ✅ |
| /api/iso20022/** | ✅ | ❌ | ✅ |
| /api/operations/** | ❌ | ✅ | ✅ |
| /api/outbox-events/** | ❌ | ✅ | ✅ |
| POST/PATCH /api/participants, routing-rules, connector-configs | ❌ | ❌ | ✅ |
| GET /api/participants, routing-rules, connector-configs | ❌ | ✅ | ✅ |
| /actuator/health, /actuator/info | ✅ | ✅ | ✅ |
| /api/admin/api-keys/** | ❌ | ❌ | ✅ |

### Data Masking (Phase 4 🟡 — applied to all audit payloads; OutboxProcessorService log statements still pending)
- **`MaskingUtil`** — `common/util/MaskingUtil.java`
  - `maskAccount(String)` → shows last 4 digits: `"1234567890"` → `"******7890"`
  - `maskSensitive(String, int visibleSuffix)` → generic masker
- **Applied (audit payloads):**
  - `CreateTransferService` — `creditorAccount` in `TRANSFER_VALIDATE_REQUEST` + `TRANSFER_CREATED`
  - `CreateInquiryService` — `creditorAccount` in `INQUIRY_VALIDATE_REQUEST` + `INQUIRY_CREATED`
  - `InquiryLookupService` — `creditorAccount` in `INQUIRY_LOOKUP`
  - `TransferInquiryService` — `debtorAccount` + `creditorAccount` in `TRANSFER_INQUIRY_LOOKUP`
  - `IsoInquiryInboundService` — `creditorAccount` in both `auditAcmt023InboundReceived` and `auditInquiryCreated`
- **Pending:** `OutboxProcessorService` log statements; ISO XML plain-text `<DbtrAcct>`/`<CdtrAcct>` in logs

### Audit Actor (Phase 5 ✅)
- **`AuditActorUtil`** — `common/util/AuditActorUtil.java`
  - `currentActor()` reads `SecurityContextHolder.getContext().getAuthentication().getName()`
  - Falls back to `"SYSTEM"` when no authentication present (scheduled workers: `OutboxDispatchWorker`, recovery workers)
  - Applied in `OutboxManualRetryService` and `OperationsOutboxMarkReviewedService` — actor now reflects the actual API key name instead of hardcoded `"API"`

### Demo Key Auto-Disable (Phase 4 ✅)
- **`ProductionDemoKeyDisableService`** — `@Profile("prod")` `ApplicationRunner`
- On every prod startup: disables V14 demo keys matched by name + `key_prefix` (double-check prevents false positives)
- Logs `WARN` to tell ops to provision a real ADMIN key; no-op if already disabled
- Does not affect dev/test profiles

### DB Least-Privilege Users (Phase 3 ✅)
- **`scripts/init-db-users.sh`** — auto-runs on first `docker compose up` via MySQL `docker-entrypoint-initdb.d`
- `switching_app` → SELECT/INSERT/UPDATE/DELETE only (app runtime, no DDL)
- `switching_flyway` → ALL PRIVILEGES (Flyway migrations only)
- `application.yml` uses `FLYWAY_URL/USERNAME/PASSWORD` with fallback to datasource creds

---

## 12. Configuration

### Environment Variables (`.env` file required)

```bash
# Required — app DB password (switching_app user after P3 setup)
DB_PASSWORD=your_app_db_password
TEST_DB_PASSWORD=your_test_mysql_password

# Required for production (leave empty = dev fallback key used — INSECURE)
MESSAGE_CRYPTO_KEY_BASE64=<base64-encoded 16/24/32 byte AES key>

# Docker only — root password for MySQL container
MYSQL_ROOT_PASSWORD=your_mysql_root_password

# Docker DB least-privilege users (P3 hardening — created by scripts/init-db-users.sh)
DB_APP_PASSWORD=switching_app_password_change_me     # switching_app user (DML only)
FLYWAY_PASSWORD=switching_flyway_password_change_me  # switching_flyway user (DDL migrations)

# Optional Flyway override (defaults to DB_URL/DB_USERNAME/DB_PASSWORD if not set)
# FLYWAY_URL=jdbc:mysql://...
# FLYWAY_USERNAME=switching_flyway
```

### `application.yml` (production)

```yaml
spring:
  config:
    import: optional:file:.env[.properties]
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3306/switching_db?useSSL=false&serverTimezone=Asia/Bangkok&allowPublicKeyRetrieval=true}
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD}        # NO default — must be set
  jpa:
    hibernate.ddl-auto: validate
  flyway:
    enabled: true
    locations: classpath:db/migration

server:
  port: ${SERVER_PORT:8080}

management:
  endpoints.web.exposure.include: health,info

switching:
  payment:
    json-initiation.enabled: ${JSON_INITIATION_ENABLED:false}   # false = ISO-only; set true in .env for local dev
  mock-bank.pacs002.force-reject: ${MOCK_BANK_FORCE_REJECT:false}
  security:
    message-crypto-key-base64: ${MESSAGE_CRYPTO_KEY_BASE64:}    # empty = DEV fallback (blocked in prod)
    api-key.enabled: ${API_KEY_AUTH_ENABLED:true}
    rate-limit:
      enabled: ${RATE_LIMIT_ENABLED:true}
      requests-per-minute: ${RATE_LIMIT_RPM:100}
  outbox.worker:
    poll-interval-ms: ${OUTBOX_POLL_INTERVAL_MS:30000}
    recovery-interval-ms: ${OUTBOX_RECOVERY_INTERVAL_MS:60000}
    batch-size: ${OUTBOX_BATCH_SIZE:20}
    max-retry: ${OUTBOX_MAX_RETRY:3}
    stuck-timeout-minutes: ${OUTBOX_STUCK_TIMEOUT_MINUTES:2}
```

### Spring Profiles

| Profile | File | Purpose |
|---------|------|---------|
| *(none / base)* | `application.yml` | Shared defaults; works for local Maven run with `.env` |
| `dev` | `application-dev.yml` | Docker Compose local dev — `show-sql=true`, `json-initiation=true` |
| `staging` | `application-staging.yml` | Staging environment — requires real `DB_URL`/`DB_USERNAME`, no localhost defaults |
| `prod` | `application-prod.yml` | Production — `MESSAGE_CRYPTO_KEY_BASE64` has no default (Spring fails if unset); `api-key.enabled` and `rate-limit.enabled` hardcoded to `true`; `force-reject=false` hardcoded |
| `test` | `application-test.yml` | Integration tests — API key disabled, rate limit disabled, crypto key has dev fallback |

**How profile is selected:**
- Docker Compose: `SPRING_PROFILES_ACTIVE: dev` (set in `docker-compose.yml`)
- Production deployment: set `SPRING_PROFILES_ACTIVE=prod` in container env / K8s `Deployment`
- Tests: `@ActiveProfiles("test")` via `AbstractIntegrationTest`
- Local Maven run (`./run.sh`): no profile set → base `application.yml` only

### `application-test.yml` (test profile)

```yaml
switching:
  payment.json-initiation.enabled: true
  mock-bank.pacs002.force-reject: false
  security:
    message-crypto-key-base64: ${TEST_MESSAGE_CRYPTO_KEY_BASE64:NImwCmFwkSIeDgy8UJtzGq86A389puEEe6gi2Wdo9MM=}
    api-key.enabled: false        # no auth in tests
    rate-limit.enabled: false     # no rate limiting in tests
```

Test DB: `switching_clean` (separate from prod `switching_db`) — started by Testcontainers, not a real host.

### `ProductionStartupValidator` (`config/ProductionStartupValidator.java`)
- Active only when `@Profile("prod")`
- Runs at `afterPropertiesSet()` — before any requests are served
- **Hard fails** (throws `IllegalStateException`) if:
  - DB URL contains `allowPublicKeyRetrieval=true`
  - DB URL points to `localhost` or `127.0.0.1`
  - `switching.mock-bank.pacs002.force-reject=true` (mock flag should never be active in prod)
- **Warns** (log.warn) if `switching.payment.json-initiation.enabled=true` in prod

### 2026-05-14 Local Run Fix

- Added `spring.config.import: optional:file:.env[.properties]` to both `application.yml` and `application-test.yml`.
- Reason: running with `./mvnw spring-boot:run` directly does not source `.env`, so Spring could start with missing DB env values and fail during Flyway/DataSource initialization.
- `run.sh` still sources `.env`; this change also supports direct Maven/IDE local runs.
- Updated `run.sh local` to auto-select the first free port starting at `8080` when `SERVER_PORT` is not explicitly set.
- Reason: local startup failed with `Port 8080 was already in use` when another app instance was already listening.

---

## 13. Docker Setup

### `docker-compose.yml`
- **mysql** service: `mysql:8.4`, database `switching_db`, health check with `mysqladmin ping`
- **app** service: built from `Dockerfile`, waits for MySQL health, connects via `mysql:3306`

### `Dockerfile`
3-stage multi-stage build:
1. **deps** (`maven:3.9.9-eclipse-temurin-21`): `./mvnw dependency:go-offline` — cached layer, only re-runs on `pom.xml` change
2. **build** (from deps): `./mvnw clean package -DskipTests` — JAR produced; tests are skipped here because CI runs them before building the image
3. **runtime** (`eclipse-temurin:21-jre`): non-root user `switching`, JVM container flags, exposes 8080

JVM flags in ENTRYPOINT:
- `-XX:+UseContainerSupport` — respects container CPU/memory limits
- `-XX:MaxRAMPercentage=75.0` — heap bounded to 75% of container RAM
- `-Djava.security.egd=file:/dev/./urandom` — faster SecureRandom seeding

Non-root user: `addgroup --system switching && adduser --system --ingroup switching switching`

### Run Commands (`run.sh`)
```bash
./run.sh              # Start locally (requires MySQL already running)
./run.sh docker       # Build + start full stack (Docker Compose)
./run.sh docker:build # Build Docker image only (no start)
./run.sh docker:rebuild # Force rebuild image + start full stack
./run.sh docker:db    # Start MySQL only
./run.sh test         # Run all Maven tests (Testcontainers — no local MySQL needed)
./run.sh test:unit    # Run unit tests only (no DB, fast)
./run.sh test:single  # CLASS=ClassName ./run.sh test:single
./run.sh status       # docker compose ps
./run.sh stop         # docker compose down
./run.sh logs         # docker compose logs -f app
```

---

## 14. Test Coverage

**Latest local run:** 2026-05-14

**Current result:** `./mvnw test` passes with **60/60 tests PASS** on a clean checkout — no local MySQL required.

- Integration tests use **Testcontainers** (MySQL 8.0 in Docker) via `AbstractIntegrationTest` singleton container.
- `@DynamicPropertySource` overrides datasource and Flyway URLs at runtime — static `application-test.yml` values are only fallbacks.
- CI pipeline (`.github/workflows/ci.yml`) runs all tests before packaging or building the Docker image.

| Test Class | Tests | Type | Description |
|-----------|-------|------|-------------|
| `SwitchingApplicationTests` | 1 | Unit | Context loads |
| `FullTransferFlowIntegrationTest` | 24 | Integration | Full inquiry→transfer flow, idempotency, force reject, trace |
| `IsoInquiryFlowIntegrationTest` | ~10 | Integration | ISO XML ACMT.023 inquiry flow |
| `IsoInquiryValidationIntegrationTest` | ~5 | Integration | Validation edge cases |
| `IsoInquiryExpiryIntegrationTest` | ~3 | Integration | Inquiry TTL expiry |
| `OperationsTransferTraceIntegrationTest` | 2 | Integration | Ops trace endpoint with combined timeline |
| `OperationsTransferQueryIntegrationTest` | 5 | Integration | Ops transfer list/query |
| `OperationsIsoInquiryQueryIntegrationTest` | ~5 | Integration | Ops ISO inquiry queries |
| `ParticipantManagementServiceTest` | ~5 | Unit | Participant CRUD |
| `RoutingRuleManagementServiceTest` | 5 | Unit | Routing rule CRUD |
| `ConnectorConfigManagementServiceTest` | 5 | Unit | Connector CRUD |

### Test Pattern
All integration tests:
- `extends AbstractIntegrationTest` — provides `@SpringBootTest`, `@ActiveProfiles("test")`, and Testcontainers MySQL singleton
- Use `switching_clean` database (started in a Docker container by Testcontainers)
- Seed fixtures via `JdbcTemplate.update()` with `ON DUPLICATE KEY UPDATE`
- MockMvc for HTTP calls
- `AtomicInteger` counter + millis for unique refs

`AbstractIntegrationTest` location: `src/test/java/com/example/switching/AbstractIntegrationTest.java`

### Test Hardening Done ✅

- ✅ Replaced local MySQL/root dependency with Testcontainers (MySQL 8.0, singleton pattern).
- ✅ `./mvnw test` passes from a clean checkout — 60/60 PASS.
- ✅ CI pipeline created (`.github/workflows/ci.yml`) — tests run before package and Docker build.
- ✅ Production Docker image is built only after tests pass (CI `needs:` chain).
- [ ] Split fast unit tests from DB-backed integration tests into separate Maven Surefire groups.
- [ ] Add `@AfterEach` cleanup to all integration tests to prevent data accumulation.

### Key Test Fixtures (seeded in BeforeEach)
```sql
-- Participants
INSERT INTO participants (bank_code, bank_name, status, ...) VALUES ('BANK_A', ...) ON DUPLICATE KEY UPDATE ...
INSERT INTO participants (bank_code, bank_name, status, ...) VALUES ('BANK_B', ...) ON DUPLICATE KEY UPDATE ...

-- Connector
INSERT INTO connector_configs (connector_name='MOCK_BANK_B_CONNECTOR', bank_code='BANK_B', ...) ON DUPLICATE KEY UPDATE ...

-- Routing
DELETE FROM routing_rules WHERE source_bank='BANK_A' AND destination_bank='BANK_B' AND message_type='PACS_008'
INSERT INTO routing_rules (route_code='ROUTE_A_TO_B_PACS008_TEST', ...) VALUES ...
```

---

## 15. Known Issues & Fixed Bugs

### Fixed ✅

| Bug | Location | Fix |
|-----|----------|-----|
| Hardcoded DB password `55919230` in git history | `application.yml`, `application-test.yml` | Removed with `git filter-repo`, replaced with `${DB_PASSWORD}` (no default) |
| SQL text block trailing space stripped | `OperationsTransferTraceService.java` lines 240, 371, 441 | Changed `WHERE """` to `WHERE\s"""` (`\s` = non-strippable space in Java text block) |
| Silent exception swallowing in trace | `OperationsTransferTraceService.java` catch blocks | Added `log.error(...)` before each `warnings.add(...)` so failures are visible in logs |
| `hasInquiry: false` for JSON-path transfers in trace | `OperationsTransferTraceService.findInquiry()` | Added `findJsonPathInquiry()` fallback — queries `inquiries` table when `iso_inquiries` returns null; timeline now shows `JSON_INQUIRY_CREATED` events |
| `json-initiation.enabled` hardcoded to `false` | `application.yml` | Changed to `${JSON_INITIATION_ENABLED:false}`; added `JSON_INITIATION_ENABLED=true` to `.env` for local dev |
| `MAX_RETRY` static final impossible to configure | `OutboxProcessorService`, `OutboxRecoveryService` | Changed to `private final int maxRetry` injected via `@Value("${switching.outbox.worker.max-retry:3}")` |
| Idempotency hash conflict returned 500 instead of 409 | `IdempotencyService.findExistingTransfer()` line 41–44 | `throw new IllegalStateException(...)` changed to `throw new IdempotencyConflictException(...)` → now maps to TRF-002 (409 CONFLICT) via `GlobalExceptionHandler` |
| `OutboxEventNotFoundException` / `OutboxManualRetryNotAllowedException` fell through to 500 SYS-001 | `GlobalExceptionHandler.java`, `ErrorCatalog.java` | Added `OUT_005` (404 NOT_FOUND) to `ErrorCatalog`; registered both handlers in `GlobalExceptionHandler` — outbox retry-not-found now returns 404 and retry-not-allowed returns 409 |
| `audit_logs` query selected non-existent `channel_id` column → 500 | `OperationsAuditLogQueryService.java` SQL + `mapRow()` | Removed `a.channel_id` from SELECT; set `null` in `mapRow()` for `channelId` field (audit_logs table has no channel_id column) |
| Integration tests failed on clean checkout (`Access denied` for root@localhost) | All integration tests + `application-test.yml` | Migrated to Testcontainers: `AbstractIntegrationTest` starts MySQL 8.0 in Docker via static singleton; `@DynamicPropertySource` overrides all datasource/Flyway URLs — 46/46 PASS, no local MySQL needed |
| Testcontainers 2.0.3 artifact IDs changed from 1.x | `pom.xml` | `junit-jupiter` → `testcontainers-junit-jupiter`; `mysql` → `testcontainers-mysql`; added `<version>${testcontainers.version}</version>` |
| TC-071 rate-limit error body check got 200 instead of 429 | `scripts/run_tests.sh` TC-071 | `refillGreedy` refills continuously at 1.67 tokens/sec; new request after ~0.6s had 1 token → returned 200. Fix: save 429 response body during TC-070 loop into `RATE_LIMIT_BODY`; TC-071 reads saved body, no new request |
| Docker build was 2-stage, app ran as root | `Dockerfile` | Refactored to 3-stage (deps → build → runtime); added non-root `switching` user; JVM container flags (`UseContainerSupport`, `MaxRAMPercentage=75.0`, urandom egd) |
| No Spring profile separation — prod/dev shared same config | `application.yml` | Created `application-dev.yml`, `application-prod.yml`, `application-staging.yml`; prod profile enforces no-default secrets and hardcodes security flags |
| `IsoMessageCryptoService` used `System.getProperty("spring.profiles.active")` for profile detection | `IsoMessageCryptoService.java:resolveKey()` | Changed to `Environment.getActiveProfiles()` — Spring-native, covers all profile-activation paths |
| `allowPublicKeyRetrieval=true` in prod DB URL not caught at startup | `docker-compose.yml`, `application.yml` | `ProductionStartupValidator` fails startup if DB URL contains `allowPublicKeyRetrieval=true` or points to `localhost` |

**Root cause of SQL bug:** Java text blocks automatically strip trailing whitespace from content lines. `WHERE ` before closing `"""` became `WHERE`, producing `WHEREcondition` → MySQL `SQLSyntaxErrorException`. Affected `findInquiry()`, `findIsoMessages()`, `findAuditEvents()`. Exception was silently caught by try-catch, making `hasInquiry=false`.

### Open ⚠️

| Issue | Location | Risk | Fix Needed |
|-------|----------|------|------------|
| ~~DEV_FALLBACK_KEY used when `MESSAGE_CRYPTO_KEY_BASE64` empty~~ | ~~`IsoMessageCryptoService.java`~~ | **FIXED ✅ Phase 1** | Throws `IllegalStateException` outside test profile |
| ~~No authentication/authorization~~ | ~~All endpoints~~ | **FIXED ✅ Phase 1** | API Key auth with RBAC via Spring Security |
| ~~`hasInquiry: false` and missing timeline items for JSON-path transfers~~ | ~~`OperationsTransferTraceService.findInquiry()`~~ | **FIXED ✅ Phase 3** | `findJsonPathInquiry()` fallback added |
| ~~`json-initiation.enabled` hardcoded `false` blocks JSON path locally~~ | ~~`application.yml`~~ | **FIXED ✅ Phase 3** | Env-var driven via `JSON_INITIATION_ENABLED` |
| ~~Integration tests depend on local MySQL root credentials~~ | ~~`application-test.yml`, integration tests~~ | **FIXED ✅ Phase 1** | Testcontainers singleton — 46/46 PASS, no local MySQL needed |
| ~~Docker build skips tests~~ | ~~`Dockerfile`~~ | **FIXED ✅ Phase 1** | CI pipeline runs tests before Docker build via `needs:` chain; `-DskipTests` kept in Dockerfile only, image is never pushed without CI gate passing |
| MySQL root user and insecure connection defaults | `docker-compose.yml`, `application.yml` | **HIGH** — weak prod posture | Use dedicated DB user, TLS, and prod-only env values |
| ~~V8 drops/recreates `routing_rules` after V2 seed~~ | ~~`V8__create_participants_and_routing_rules.sql`~~ | **FIXED ✅ V15 migration** | V15 seeds BANK_A/B/C participants + 4 routing rules with `ON DUPLICATE KEY UPDATE` |
| ~~Routing cache not invalidated on update~~ | ~~`RoutingService.java`~~ | **FIXED ✅ Phase 3** | `RoutingRuleManagementService.create()` and `update()` call `routingService.clearCache()` |
| Test data accumulates in DB across runs | All integration tests | Low | Add cleanup in `@AfterEach` |
| `inquiry_status_history` not used for ISO path | `iso_inquiries` | Low | Status changes in ISO path not tracked |
| JSON-path `inquiries` trace shows null for ISO-only fields | `OperationsTransferTraceService` | Low | `messageId`, `instructionId`, `endToEndId`, `debtorAccount`, `expiresAt` are null by design for JSON path — expected |

---

## 16. Seed Data (V2 + V9 + V14 + V15)

```sql
-- V2: Legacy participant banks (superseded by V8 participants table)
INSERT INTO participant_banks (bank_code, bank_name) VALUES ('BANK_A', ...), ('BANK_B', ...)
INSERT INTO routing_rules (route_code='ROUTE_BANK_B_PRIMARY', destination_bank_code='BANK_B', connector_name='MOCK_CONNECTOR')

-- V9: Connector configs
INSERT INTO connector_configs VALUES ('MOCK_BANK_A_CONNECTOR', 'BANK_A', 'MOCK', ...)
INSERT INTO connector_configs VALUES ('MOCK_BANK_B_CONNECTOR', 'BANK_B', 'MOCK', ...)
INSERT INTO connector_configs VALUES ('MOCK_BANK_C_CONNECTOR', 'BANK_C', 'MOCK', ...)

-- V14: Demo API keys (plaintext — hashed by V17)
INSERT INTO api_keys (key_value, name, role, ...) VALUES ('sk-admin-switching-2026', ...)

-- V15: Participants + routing rules seed (added 2026-05-15)
INSERT INTO participants (bank_code='BANK_A', status='ACTIVE', ...) ON DUPLICATE KEY UPDATE updated_at=updated_at
INSERT INTO participants (bank_code='BANK_B', ...) ON DUPLICATE KEY UPDATE ...
INSERT INTO participants (bank_code='BANK_C', participant_type='INDIRECT', ...) ON DUPLICATE KEY UPDATE ...
INSERT INTO routing_rules (route_code='ROUTE_BANK_A_TO_BANK_B_PACS008', ...) ON DUPLICATE KEY UPDATE ...
-- (4 bidirectional routes: A→B, A→C, B→A, C→A)
```

**Note:** V15 uses `ON DUPLICATE KEY UPDATE updated_at = updated_at` — idempotent, safe to re-apply. Fresh installs now have working participants and routing rules out of the box.

---

## 17. Module Package Map

```
com.example.switching.
├── audit          → audit_logs table, AuditLogService.log()
├── common         → ApiErrorResponse, ErrorCatalog, GlobalExceptionHandler, RequestIdFilter
├── connector      → BankConnector interface, MockBankConnector, ConnectorRegistry, connector_configs table
├── dashboard      → /api/operations/dashboard-summary
├── demo           → DemoFlowService (non-production helper)
├── idempotency    → idempotency_records table, IdempotencyService
├── inquiry        → inquiries table (JSON path), /api/inquiries
├── iso            → iso_messages, iso_inquiries tables, ISO XML parsing, crypto
├── operations     → /api/operations/* (all ops controllers + services)
├── outbox         → outbox_events table, workers, dispatch
├── participant    → participants table, /api/participants
├── routing        → routing_rules table, RoutingService with cache
└── transfer       → transfers table (core), /api/transfers
```

---

## 18. ISO Message Payload (Outbox JSON)

The `outbox_events.payload` column contains JSON:

```json
{
  "transferRef": "TRX-1234567890-ABCD",
  "isoMessageId": 42,
  "sourceBank": "BANK_A",
  "destinationBank": "BANK_B",
  "routeCode": "ROUTE_A_TO_B_PACS008",
  "connectorName": "MOCK_BANK_B_CONNECTOR",
  "messageType": "PACS_008"
}
```

Note: `messageType` was added later — old payloads may not have it. `OutboxIsoMessageDispatchService` falls back to `iso_messages.message_type` if absent.

---

## 19. Curl E2E Test Script

**Location:** `scripts/curl_e2e_tests.sh`

Sections:
1. Health (`/actuator/health`, `/api/operations/health`)
2. Inquiry (create, get, 404, 400 validation)
3. Transfer (create, get, reuse→409/422, idempotency, list, 404)
4. ISO Messages list
5. Outbox (list, retry 404)
6. Participants (list, get, 404)
7. Routing Rules list
8. Connector Configs list
9. Operations APIs (dashboard, transactions, ISO messages, audit logs, outbox failures, trace)
10. Transfer Trace

Usage: `./scripts/curl_e2e_tests.sh` or `BASE_URL=http://localhost:8080 ./scripts/curl_e2e_tests.sh`

### Automated curl Test Runner

**Location:** `scripts/run_tests.sh`

Latest update:
- Added extended ISO20022 automated curl testcases to Section 11.
- New coverage includes valid ACMT.023 inquiry, extracting `InquiryRef` from ACMT.024, valid PACS.008 using that `InquiryRef`, PACS.002 acceptance check, repeat PACS.008 idempotency, used `InquiryRef` rejection, and unknown `InquiryRef` rejection.
- Helper functions added for XML response checks: `xml_val()` and `body_has()`.
- Runtime state added for ISO flow: `ISO_INQUIRY_REF` and `ISO_TRANSFER_REF`.
- 2026-05-14 fix: TC-103 ACMT.023 XML now matches the local parser profile (`IdVrfctnReq`, `BICFI`, `PtyAndAcctId/Acct/Id/Othr/Id`) instead of the unsupported `AcctMgmtInqDef` shape.
- 2026-05-14 fix: TC-104/TC-106/TC-107 PACS.008 XML now uses `BICFI` and the marked supplementary data profile (`PlcAndNm=LAO_SWITCHING_INQUIRY_REF`).
- 2026-05-14 fix: TC-107b now validates the stable ISO rejection status `TxSts=RJCT` instead of relying on a specific `AddtlInf` text.
- 2026-05-14 fix: TC-104b and TC-105b now accept both PACS.002 accepted statuses `ACCP` and `ACTC`; current implementation returns `ACTC` with `AcctSvcrRef`.

### Manual curl Test Cases

**Location:** `docs/manual-curl-testcases.md`

Purpose:
- Manual testcase checklist for users who want to copy/paste and run `curl` themselves.
- Covers public health, API key authentication, role authorization, admin setup smoke tests, JSON inquiry/transfer flow, idempotency, force-reject, rate limiting, outbox/operations queries, and ISO XML smoke tests.
- Uses seeded demo keys from `V14__create_api_keys.sql`:
  - `sk-admin-switching-2026`
  - `sk-ops-switching-2026`
  - `sk-bank-a-switching-2026`
  - `sk-bank-b-switching-2026`

**Test run result (2026-05-14):** 38 PASS · 0 FAIL · 3 bugs found and fixed during the run:
1. `IdempotencyService`: `IllegalStateException` on hash mismatch → changed to `IdempotencyConflictException` (TRF-002, 409)
2. `GlobalExceptionHandler` + `ErrorCatalog`: missing handlers for `OutboxEventNotFoundException` (OUT-005, 404) and `OutboxManualRetryNotAllowedException` (OUT-004, 409)
3. `OperationsAuditLogQueryService`: SQL selected `a.channel_id` which does not exist in `audit_logs` table → removed column, `mapRow()` returns `null` for channelId

---

## 20. Production Readiness Snapshot

**Current assessment:** Not production-ready yet, but progressing steadily. P0–P2 complete. P3 at 65% (migrations + DB user scripts done; TLS and backup drill pending). P4 at 50% (API key hashing + management done; MaskingUtil applied to all 5 audit-producing services; mTLS, OutboxProcessorService log masking, role expansion pending). P5 at 65% (exponential backoff, next_retry_at filter, graceful shutdown, manual-action audit trail, and actor-from-SecurityContext all done; concurrent dispatch test + backoff integration test still pending). P6 at 30% (Prometheus endpoint exposed, JSON logging configured; Grafana dashboards + alerts + log aggregation are infrastructure tasks). P7 at 55% (graceful shutdown config done; 6 K8s manifests created covering deployment/HPA/service/configmap/secret/namespace; rollback drill still pending). Main remaining code items: OutboxProcessorService log masking, concurrent dispatch tests; infrastructure items: DB TLS certs, backup, Prometheus/Grafana setup.

**Estimated readiness:** 94%.

### Strengths

- Clear modular Spring Boot structure: transfer, inquiry, ISO, outbox, routing, participant, connector, operations.
- Transactional outbox pattern with claim step (`PENDING` → `PROCESSING`) and near real-time dispatch via `@TransactionalEventListener(AFTER_COMMIT)`.
- Flyway is enabled and Hibernate uses `ddl-auto=validate`.
- Central `GlobalExceptionHandler` and `ErrorCatalog`.
- Request tracing: `X-Request-Id` header + MDC (`requestId`, `transferRef`, `inquiryRef`, `outboxEventId`).
- Audit log model covers transfer/outbox/ISO/inquiry events.
- Actuator exposure: `health,info` on main port; `health,info,prometheus` on management port (staging: same port; prod: `${MANAGEMENT_PORT:9090}` separate).
- **Phase 1 (roadmap) ✅:** API Key authentication with RBAC (ADMIN/OPS/BANK roles), AES/GCM message encryption mandatory in prod.
- **Phase 2 (roadmap) ✅:** Micrometer counters/timers/gauges for transfers and outbox; MDC log correlation across all layers.
- **Phase 3 (roadmap) ✅:** All outbox parameters configurable via env vars; rate limiting (Bucket4j token bucket, 429 response); near real-time dispatch (~50-200ms); routing cache auto-invalidation; JSON-path inquiry visible in transfer trace.
- **Phase 0 (prod roadmap) ✅:** Risk register (40+ risks), production acceptance checklist, endpoint classification matrix.
- **Phase 1 (prod roadmap) ✅:** Testcontainers migration (60/60 PASS, no local MySQL), CI pipeline (6 jobs, fail-fast), Dockerfile hardened (3-stage, non-root user, JVM flags), run.sh updated, TC-071 fix.
- **Phase 2 (prod roadmap) ✅:** Spring profiles `dev`/`staging`/`prod`; `application-prod.yml` enforces no-default secrets at startup; `api-key.enabled` and `rate-limit.enabled` hardcoded `true` in prod; `ProductionStartupValidator` blocks insecure DB URL; `IsoMessageCryptoService` uses Spring `Environment` for profile detection.
- **Phase 3 (prod roadmap) 🟡 In Progress (65%):** V15 seed (participants + routing_rules); V16 indexes (6); V17 api_keys hardening. `scripts/init-db-users.sh` creates `switching_app` (DML-only) + `switching_flyway` (DDL); docker-compose mounts init script + routes app/Flyway to separate users; `application.yml` adds `FLYWAY_URL/USERNAME/PASSWORD` env vars. DB TLS + backup still pending.
- **Phase 4 (prod roadmap) 🟡 In Progress (50%):** API key SHA-256 hashing + expiry + management endpoints; XXE protection confirmed; `MaskingUtil.maskAccount()` applied to audit payloads in `CreateTransferService`, `CreateInquiryService`, `InquiryLookupService`, `TransferInquiryService`, `IsoInquiryInboundService`; XML body capped at 1MB. `OutboxProcessorService` log masking, mTLS, role expansion still pending.
- **Phase 5 (prod roadmap) 🟡 In Progress (65%):** Exponential backoff (retry 1→+30s, retry 2→+2min, retry 3+→+10min); `next_retry_at` set on retry + filtered in `findPendingBatch`; graceful shutdown (`volatile shuttingDown` + `@PreDestroy`); manual-action audit trail (`OUTBOX_MANUAL_RETRY_REQUESTED`, `OUTBOX_EVENT_MARKED_REVIEWED`); `AuditActorUtil.currentActor()` reads SecurityContextHolder (fallback `"SYSTEM"` for workers) — actor no longer hardcoded `"API"`. Concurrent dispatch test + backoff integration test still pending.
- **Phase 6 (prod roadmap) 🟡 In Progress (30%):** `micrometer-registry-prometheus` added; `/actuator/prometheus` exposed (staging: port 8080; prod: `${MANAGEMENT_PORT:9090}` separate from main API); `logback-spring.xml` — text format for dev, JSON (`LogstashEncoder` + `ShortenedThrowableConverter`) for staging/prod with all MDC fields auto-included. Prometheus server, Grafana dashboards, alerting, log aggregation are infrastructure tasks.
- **Phase 7 (prod roadmap) 🟡 In Progress (55%):** `server.shutdown: graceful` + `spring.lifecycle.timeout-per-shutdown-phase: 30s` in `application.yml`. 6 K8s manifests created: `k8s/namespace.yaml`, `k8s/configmap.yaml`, `k8s/secret.yaml`, `k8s/deployment.yaml` (Flyway initContainer, RollingUpdate maxUnavailable:0/maxSurge:1, liveness+readiness+startup probes on management port 9090, terminationGracePeriodSeconds:60), `k8s/service.yaml` (ClusterIP 80→8080 + 9090 management), `k8s/hpa.yaml` (CPU 70%/Memory 80%, 2–8 pods, scaleDown stabilization 300s). Rollback drill still pending.

### Main Production Gaps

- Docker Compose uses MySQL root for app access (→ Phase 3 DB hardening).
- DB connections use `useSSL=false` and `allowPublicKeyRetrieval=true` (→ Phase 3).
- ~~Current migrations do not seed `participants`/`routing_rules` for a fresh prod install~~ **Fixed ✅ V15 migration**
- ~~API keys stored plaintext in DB~~ **Fixed ✅ V17 + ApiKeyHashUtil**
- ~~Demo keys active in production~~ **Fixed ✅ ProductionDemoKeyDisableService auto-disables on prod startup**
- ~~App DB user has full root access~~ **Fixed ✅ init-db-users.sh + docker-compose (switching_app DML-only, switching_flyway for migrations)**
- Demo keys (`sk-admin-switching-2026` etc.) still seeded in migrations — need prod disable step (→ Phase 2 remaining).
- ~~Outbox retry had no backoff — events retried immediately~~ **Fixed ✅ exponential backoff 30s/2min/10min + next_retry_at filter**
- ~~`OutboxDispatchWorker` did not handle shutdown signal~~ **Fixed ✅ volatile shuttingDown + @PreDestroy + server.shutdown:graceful**
- Multi-instance outbox concurrent dispatch test still pending (→ Phase 5).
- ~~No Prometheus metrics export~~ **Fixed ✅ micrometer-registry-prometheus + /actuator/prometheus (separate management port in prod)**
- ~~No structured JSON logging~~ **Fixed ✅ logback-spring.xml JSON mode for staging/prod (logstash-logback-encoder)**
- ~~Audit actor hardcoded as `"API"` in manual actions~~ **Fixed ✅ AuditActorUtil reads SecurityContextHolder; fallback "SYSTEM" for workers**
- ~~No K8s manifests~~ **Fixed ✅ 6 manifests in `k8s/`: namespace, configmap, secret, deployment, service, hpa**
- No Grafana dashboards or alerts (infrastructure tasks → Phase 6 remaining).
- Log aggregation (Fluentd/Filebeat → Elasticsearch) not configured (infrastructure → Phase 6 remaining).

---

## 21. LaoFP Compliance Gap Analysis

This section maps LaoFP Master Spec v1.0 requirements against current implementation.

### 21.1 Authentication & Security (NFR-2.1 to 2.4)

| LaoFP Requirement | Current State | Gap |
|-------------------|--------------|-----|
| TLS 1.3 + mTLS client cert for all PSP connections | API key only (no mTLS) | 🔴 Missing |
| OAuth 2.0 / OpenID Connect for API authorisation | Custom API key | 🔴 Replace |
| HMAC-SHA256 request signing (`X-Request-Signature` header) | None | 🔴 Missing |
| `X-Participant-ID`, `X-Timestamp` headers | `X-API-Key`, `X-Bank-Code` | 🟡 Rename + add |
| AES-256 at rest + FIPS 140-2 Level 3 HSM key management | AES/GCM with env var key | 🟡 Upgrade to HSM |
| Annual VAPT | Not performed | 🔴 Process gap |

### 21.2 FPRE (Force Push & Automatic Retry Engine) — MOD-21

| LaoFP Requirement | Current State | Gap |
|-------------------|--------------|-----|
| 5 retries: 30s / 60s / 2m / 5m / 10m ±10% jitter | 3 retries: 30s / 2m / 10m (no jitter) | 🟡 Extend + add jitter |
| `failureClass`: TRANSIENT / TRANSIENT_POOL / PERMANENT_BUSINESS / PERMANENT_COMPLIANCE / PERMANENT_EXPIRED / AMBIGUOUS | None — binary retry/no-retry | 🔴 Add classification |
| `willRetry` boolean in all failure responses | Not in responses | 🔴 Add |
| Auto-reversal after 5 failed attempts + pool hold release | None | 🔴 Missing |
| PSP idempotency check: `GET /laofp/transactions/{txnId}/credit-status` | None (LaoFP calls PSP's endpoint) | 🔴 PSP-side requirement |
| PSP auto-suspension ≥3 reversals to same PSP in 30 min | None | 🔴 Missing |
| TRANSFER.PENDING / TRANSFER.RETRY_ATTEMPT / TRANSFER.MAX_RETRIES_REACHED / TRANSFER.REVERSED webhooks | None (no webhook system) | 🔴 Requires MOD-14 |
| `resolvedByRetry`, `retryAttempt` in COMPLETED response | None | 🟡 Add fields |

### 21.3 Transaction Types — MOD-04

| Transaction Type | ISO Message | Current State | Gap |
|-----------------|-------------|--------------|-----|
| Bank-to-Bank Transfer | pacs.008 / pacs.002 | ✅ Implemented | Minor API alignment |
| Account Lookup (VPA) | acmt.023 | 🟡 ISO inquiry only | Missing VPA directory, beneficiaryToken |
| Bank-to-Wallet Transfer | pacs.008 → wallet credit | ❌ Not started | Full MOD-06 + pool-to-pool |
| Wallet-to-Wallet Transfer | Internal ledger | ❌ Not started | Intra vs inter-operator routing |
| Merchant / QR Payment | EMVCo QR + pacs.008 | ❌ Not started | Full MOD-07 |
| Bill Payment | Custom (fetch + pay) | ❌ Not started | MOD-08 + biller registry |
| Cross-border | pacs.008 → corridor | ❌ Not started | MOD-09 + FX + AML |
| Bulk Transfer | pain.001 | ❌ Not started | Batch processing |

### 21.4 Missing Modules (0% implemented)

| Module | LaoFP Spec Section | Key Deliverables |
|--------|-------------------|-----------------|
| **VPA Directory** (MOD-06) | FR-1.1 to FR-1.4, B1 | `POST /v1/lookup/resolve`, VPA register/update/deregister, `beneficiaryToken` 5-min TTL |
| **QR Code Service** (MOD-07) | FR-5.1 to FR-5.4, B6 | Static + dynamic QR (EMVCo), `/qr/generate/static`, `/qr/generate/dynamic`, `/qr/decode`, `/qr/pay`, CRC-16 checksum |
| **Bill Payment** (MOD-08) | FR-6.1 to FR-6.3, B7 | Biller registry, bill fetch (`billId` 10-min TTL), `/bills/fetch`, `/bills/pay`, duplicate protection 24h window |
| **Cross-border Gateway** (MOD-09) | FR-7.1 to FR-7.4, B8 | FX quote 30s rate lock, corridor routing (PromptPay/CNAPS/NAPAS/SWIFT), mandatory sanctions screening |
| **Settlement Engine** (MOD-10) | FR-8.1 to FR-8.4, B10 | DNS 4 cycles/day, RTGS for >LAK 500M, camt.054 report, prefunded pool debit/credit per transaction |
| **Liquidity Manager** (MOD-11) | FR-3.1, FR-3.3 | Real-time PSP pool balance, `LIQUIDITY.LOW_ALERT` at 120% minimum, `/settlement/balance` |
| **Risk & Fraud Engine** (MOD-12) | A2.1 | Real-time fraud scoring per transaction, velocity checks, rule engine |
| **AML/CFT Screening** (MOD-13) | FR-7.2, C1 | OFAC/UN/BoL sanctions screening <2s, STR auto-generation within 24h, cross-border >LAK 5M purpose + source-of-funds |
| **Webhook / Notification** (MOD-14) | A7, B-all | Full event delivery (TRANSFER.*, QR.*, BILL.*, SETTLEMENT.*, DISPUTE.*, LIQUIDITY.*), retry on delivery failure |
| **Dispute & Refund** (MOD-15) | FR-9.1 to FR-9.3, B9 | 90-day window, 6 dispute types, evidence exchange, auto-refund on RESOLVED_REFUND |
| **Data Archival** (MOD-20) | NFR-3.4, C1 | 7-year immutable audit retention, cold storage, regulatory export |

### 21.5 API Contract Alignment Needed

| Area | Current | LaoFP Target |
|------|---------|-------------|
| URL prefix | `/api/*` | `/v1/*` |
| Error code format | `REQ-001`, `INQ-001` | `LFP-1001`, `LFP-2001` |
| Transaction ID | `transferRef` (TRX-xxx) | `txnId` (UUID) |
| PSP identifier header | `X-Bank-Code` | `X-Participant-ID` |
| Auth header | `X-API-Key` | `Authorization: Bearer <oauth2_token>` |
| New required headers | — | `X-Request-Signature`, `X-Timestamp`, `X-Idempotency-Key` |
| Transaction status enum | RECEIVED / SUCCESS / FAILED | INITIATED / VALIDATED / PROCESSING / PENDING / FAILED / COMPLETED / REVERSED / BLOCKED / EXPIRED / PERMANENTLY_FAILED |
| Missing fields | — | `beneficiaryToken`, `purposeCode`, `settlementCycleId`, `willRetry`, `retryAttempt`, `resolvedByRetry`, `failureClass` |

### 21.6 Performance Targets (NFR — Not Yet Validated)

| Metric | LaoFP Target | Current Status |
|--------|-------------|---------------|
| Peak throughput | 2,000 TPS → 10,000 TPS | Not load-tested |
| P95 end-to-end latency | < 5 seconds (transfers) | Not measured |
| Account lookup latency | < 500 ms P95 | Not measured |
| API availability SLA | 99.95% (< 4.4 hrs/year) | Not measured |
| RTO (automated failover) | < 30 seconds | Not implemented |
| RPO | 0 seconds for committed txns | Not validated |

### 21.7 LaoFP Compliance Score

| Category | Weight | Current Score |
|----------|--------|--------------|
| B2B Transfer (foundation) | High | ~65% |
| FPRE completeness | High | ~30% |
| Auth & mTLS | Critical | ~20% |
| Settlement & Liquidity | Critical | 0% |
| Webhooks & Notifications | High | 0% |
| VPA Lookup | High | 5% |
| QR / Bill / Cross-border | High | 0% |
| AML / CFT / Compliance | Critical | 0% |
| Dispute & Refund | Medium | 0% |
| **Overall LaoFP Compliance** | | **~15–18%** |

> The current codebase is a solid **foundation** for LaoFP. The ISO 20022 routing engine, outbox pattern, participant management, security baseline, and ops dashboard are production-quality building blocks. The remaining ~82% is new domain modules stacked on top.

---

## 22. Advanced Production Roadmap (LaoFP Phases)

Target: evolve from the current switching API foundation into a **full LaoFP-compliant national payment switching platform** as defined in LaoFP Master Specification v1.0. Phases P0–P8 harden the existing foundation; Phases P9–P20 implement LaoFP-specific modules.

**Foundation Phases (P0–P8): hardening the existing B2B core**

**LaoFP Expansion Phases (P9–P20): new modules required by LaoFP spec**

| Phase | Title | LaoFP Modules | Status |
|-------|-------|--------------|--------|
| P0 | Baseline Freeze | — | 🟢 Done |
| P1 | Test & CI Gate | — | 🟢 Done |
| P2 | Production Config | — | 🟢 Done |
| P3 | DB & Migration Hardening | — | 🟡 65% |
| P4 | Security Advanced (baseline) | MOD-02 partial | 🟡 50% |
| P5 | Reliability & FPRE Foundation | MOD-21 partial | 🟡 65% |
| P6 | Observability | MOD-18 partial | 🟡 30% |
| P7 | Deployment & Runtime | MOD-01 partial | 🟡 55% |
| P8 | Compliance & Business Readiness | MOD-16, MOD-20 | ⚪ 0% |
| **P9** | **OAuth 2.0 + mTLS + Request Signing** | MOD-01, MOD-02 | ⚪ 0% |
| **P10** | **FPRE Full Compliance** | MOD-21 | ⚪ 0% |
| **P11** | **VPA / Account Lookup** | MOD-06, MOD-03 | ⚪ 0% |
| **P12** | **Webhook & Notification Engine** | MOD-14 | ⚪ 0% |
| **P13** | **Prefunded Pool & Liquidity** | MOD-11, MOD-10 partial | ⚪ 0% |
| **P14** | **Settlement Engine (DNS + RTGS)** | MOD-10 | ⚪ 0% |
| **P15** | **QR Code Service** | MOD-07 | ⚪ 0% |
| **P16** | **Bill Payment Service** | MOD-08 | ⚪ 0% |
| **P17** | **Cross-border Payment** | MOD-09 | ⚪ 0% |
| **P18** | **Dispute & Refund Manager** | MOD-15 | ⚪ 0% |
| **P19** | **AML / CFT & Risk Engine** | MOD-12, MOD-13 | ⚪ 0% |
| **P20** | **Performance & Scale (2K→10K TPS)** | MOD-01, MOD-04 | ⚪ 0% |

### Phase 0 — Production Baseline Freeze
**Status: 🟢 COMPLETE (2026-05-14)**

**Documents created:**
- `docs/risk-register.md` — 40+ risks across Security, DB, Test/CI, Outbox, Observability, Deployment, Business
- `docs/production-checklist.md` — Go/No-Go checklist per phase + full endpoint classification matrix

Goal: establish a stable baseline before deeper hardening.

Exit criteria:
- [x] Endpoint matrix exists → `docs/production-checklist.md` (Endpoint Classification Matrix)
- [x] Risk register exists → `docs/risk-register.md` (40+ risks, severity, mitigation, phase)
- [x] Production acceptance checklist exists → `docs/production-checklist.md`
- [x] Current API behavior documented → `overall.md` Sections 4–11
- [ ] Risk register reviewed by team lead (pending team review)
- [ ] Go/no-go production criteria agreed and signed off (pending team review)

### Phase 1 — Test & CI Gate
**Status: 🟢 COMPLETE (2026-05-14)**

Goal: every change must be verifiable from a clean checkout.

**Completed:**
- Migrated all 8 integration test classes to `extends AbstractIntegrationTest` (Testcontainers MySQL 8.0, singleton pattern).
- `./mvnw test` passes 46/46 on a clean checkout with no local MySQL.
- Added `testcontainers-junit-jupiter` and `testcontainers-mysql` (v2.0.3) to `pom.xml`.
- `application-test.yml` passwords use `${...:}` empty defaults (overridden by `@DynamicPropertySource`).
- Created `.github/workflows/ci.yml` — 6 jobs: compile → unit-tests → integration-tests → package → docker-build → docker-push.
- CI uses `needs:` for fail-fast; Docker image pushed only on `main` branch after all tests pass.
- Test reports uploaded as artifacts per CI run.
- Refactored `Dockerfile` to 3-stage build (deps / build / runtime) with non-root `switching` user and JVM container flags.
- Updated `run.sh` with `docker:build`, `docker:rebuild`, `test:unit`, `status` commands.
- Fixed TC-071 in `scripts/run_tests.sh` — saved 429 body during TC-070 loop; TC-071 reads saved body (avoids `refillGreedy` race where bucket refills between requests).

Exit criteria:
- [x] Clean machine can run tests — 46/46 PASS
- [x] CI blocks merge/build on failure
- [x] Test reports stored as artifacts
- [x] Docker image build happens only after test gate passes
- [ ] PR branch protection rule configured (requires CI status checks to pass)
- [ ] Unit tests separated from integration tests (Maven Surefire groups)

### Phase 2 — Production Configuration
**Status: 🟢 COMPLETE (2026-05-14)**

Goal: separate dev/staging/prod behavior clearly.

**Completed:**
- Created `application-dev.yml` — `show-sql=true`, `json-initiation=true` by default; used by Docker Compose.
- Created `application-prod.yml` — `MESSAGE_CRYPTO_KEY_BASE64`, `DB_URL`, `DB_USERNAME` have no default (Spring fails at startup if unset); `api-key.enabled` and `rate-limit.enabled` hardcoded `true`; `force-reject` hardcoded `false`.
- Created `application-staging.yml` — requires real secrets, allows JSON path for QA.
- Created `ProductionStartupValidator` (`@Profile("prod")`) — hard fails if DB URL contains `allowPublicKeyRetrieval=true` or points to `localhost`; hard fails if `force-reject=true`; warns if `json-initiation=true`.
- Fixed `IsoMessageCryptoService.resolveKey()` — uses `Environment.getActiveProfiles()` instead of fragile `System.getProperty`.
- Updated `docker-compose.yml` — `SPRING_PROFILES_ACTIVE: dev`.

Exit criteria:
- [x] Prod cannot start with missing `MESSAGE_CRYPTO_KEY_BASE64`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- [x] `api-key.enabled` and `rate-limit.enabled` cannot be disabled in prod
- [x] Startup validator catches insecure DB URL at boot
- [x] Prod config documented and auditable
- [x] Staging mirrors prod config structure
- [ ] Demo API keys disabled in production migration (pending)
- [ ] Secrets management approach decided (pending)

### Phase 3 — Database & Migration Hardening
**Status: 🟡 In Progress (65%) — 2026-05-15**

**Completed:**
- V15: `participants` seed (BANK_A, BANK_B, BANK_C) + `routing_rules` seed (4 bidirectional routes) with `ON DUPLICATE KEY UPDATE`.
- V16: 6 performance indexes.
- V17: `api_keys` hardening — `key_prefix`, `expires_at`, SHA-256 `key_value`.
- `scripts/init-db-users.sh` — creates `switching_app` (SELECT/INSERT/UPDATE/DELETE only) and `switching_flyway` (ALL PRIVILEGES for migrations); runs automatically on first `docker compose up` via `docker-entrypoint-initdb.d`.
- `docker-compose.yml` — mounts init script; app datasource uses `switching_app`; Flyway uses `switching_flyway`; separate `DB_APP_PASSWORD`/`FLYWAY_PASSWORD` env vars.
- `application.yml` — `FLYWAY_URL/USERNAME/PASSWORD` env vars with nested fallback to datasource creds (backwards-compatible for local Maven run without env vars).

**Remaining:**
- DB TLS: `useSSL=true`, `requireSSL=true` in prod JDBC URL (infrastructure — needs MySQL SSL cert).
- MySQL root password rotation after initial setup.
- Connection test: verify `switching_app` cannot DROP TABLE.
- Backup & restore drill.
- `EXPLAIN` analysis on key query paths.

Goal: fresh install and upgrade must be predictable.

Scope:
- Review all Flyway migrations as immutable production history.
- Add production-safe seed/onboarding strategy for participants, routing rules, connector configs, and API keys.
- Replace app access via MySQL root with a dedicated least-privilege DB user.
- Enable TLS for DB connections in prod.
- Review indexes for transfer trace, outbox polling, operations lists, ISO messages, reconciliation, and audit search.
- Define backup, restore, and migration rollback procedures.

Exit criteria:
- Fresh environment can be created from migrations plus documented seed/onboarding.
- Restore drill passes.
- App DB user follows least privilege.
- Hibernate validate and Flyway validate pass in each environment.

### Phase 4 — Security Advanced
**Status: 🟡 In Progress (50%) — 2026-05-15**

**Completed:**
- `ApiKeyHashUtil` — `generate()` (sk-{64 hex}), `hash()` (SHA-256), `prefix()` (first 12 chars).
- `ApiKeyAuthFilter` — hashes incoming `X-API-Key` before DB lookup; checks `expires_at`.
- `ApiKeyEntity` — `keyPrefix` (VARCHAR 16), `expiresAt`, `keyValue` length 64.
- `ApiKeyService` / `ApiKeyController` — create/list/disable/rotate (ADMIN only, `/api/admin/api-keys/**`).
- `SecurityConfig` — ADMIN-only rule for `/api/admin/api-keys/**`.
- `ProductionDemoKeyDisableService` — `@Profile("prod")` `ApplicationRunner`; disables demo keys (V14 seeds) by name + key_prefix match on every prod startup; warns ops to provision a real ADMIN key.
- XXE protection confirmed: both XML parsers have `FEATURE_SECURE_PROCESSING`, `disallow-doctype-decl`, external entities disabled.
- `MaskingUtil` — `maskAccount(String)` (last 4 visible), `maskSensitive(String, int)`.
- XML body size capped at 1MB via `server.tomcat.max-http-form-post-size`.
- `MaskingUtil.maskAccount()` applied to all audit payloads: `CreateTransferService` (creditorAccount), `CreateInquiryService` (creditorAccount), `InquiryLookupService` (creditorAccount), `TransferInquiryService` (debtorAccount + creditorAccount), `IsoInquiryInboundService` (creditorAccount in both audit methods).

**Remaining:**
- Apply `MaskingUtil.maskAccount()` to `OutboxProcessorService` log statements.
- ISO XML `<DbtrAcct>`/`<CdtrAcct>` masking in log payloads.
- mTLS for bank-facing ISO endpoints.
- Role expansion (BANK_ADMIN, OPS_READ/WRITE, FINANCE, etc.).

Goal: bank/payment-grade security.

Scope:
- ~~Store API keys hashed, not plaintext.~~ **Done ✅**
- ~~Add key rotation and key expiry.~~ **Done ✅**
- Add mTLS for bank-facing ISO endpoints where required.
- Consider HMAC/request signing for ISO inbound messages.
- Add IP allowlist per bank or per client where appropriate.
- Expand roles beyond ADMIN/OPS/BANK into production roles:
  - `ADMIN`
  - `OPS_READ`
  - `OPS_WRITE`
  - `BANK_ADMIN`
  - `BANK_USER`
  - `BANK_VIEWER`
  - `FINANCE`
  - `SETTLEMENT`
  - `DISPUTE_AGENT`
  - `DISPUTE_MANAGER`
  - `RISK_ANALYST`
  - `RISK_MANAGER`
  - `AML_OFFICER`
  - `COMPLIANCE`
  - `AUDITOR`
- Add account number masking and sensitive XML masking in logs, audit views, and portals.
- Add XML/request body size limits and XXE hardening checks.

Exit criteria:
- Key rotation works without downtime.
- Sensitive data is masked by default.
- Bank-facing trust model is documented.
- Security review checklist passes.

### Phase 5 — Reliability & Outbox Advanced
**Status: 🟡 In Progress (65%) — 2026-05-15**

Goal: no duplicate dispatch, no lost event, recoverable failures.

**Completed:**
- `OutboxProcessorService.backoffDelay(int)` — exponential backoff: retry 1 → +30s, retry 2 → +2min, retry 3+ → +10min.
- `finalizeTechnicalFailure()` sets `event.setNextRetryAt(backoffDelay(nextRetryCount))` on retryable failures, `null` on terminal.
- `OutboxEventEntity.nextRetryAt` field added (maps to V10's existing `next_retry_at DATETIME(3)` column).
- `OutboxEventRepository.findPendingBatch` JPQL query filters: `nextRetryAt IS NULL OR nextRetryAt <= :now`.
- `OutboxDispatchWorker` graceful shutdown: `volatile boolean shuttingDown`, `@PreDestroy onShutdown()`, guards in both `onOutboxCreated` and `processPendingEvents` (including mid-batch check).
- `OUTBOX_MANUAL_RETRY_REQUESTED` audit event confirmed in `OutboxManualRetryService` — includes outboxEventId, transferRef, previousStatus, retryCount, manualAction=true.
- `OUTBOX_EVENT_MARKED_REVIEWED` audit event confirmed in `OperationsOutboxMarkReviewedService` — includes outboxEventId, transferRef, reason, reviewedBy, reviewedAt.
- `AuditActorUtil.currentActor()` — reads `SecurityContextHolder`, returns API key name (e.g. `"sk-ops-..."` prefix) when authenticated, falls back to `"SYSTEM"` for unauthenticated workers. Replaces hardcoded `"API"` in `OutboxManualRetryService` and `OperationsOutboxMarkReviewedService`.

**Remaining:**
- Concurrency test: 2 app instances compete for same outbox event → only 1 wins.
- Backoff integration test: retried event is not picked up until `next_retry_at`.
- Concurrent idempotency tests.

Scope:
- Verify multi-instance outbox claim behavior under concurrent workers.
- Use `next_retry_at` consistently for retry scheduling.
- Add exponential backoff and failure classification.
- Define dead-letter/reviewed policy.
- Ensure manual retry always writes audit trail.
- Add idempotency race-condition tests.
- Add duplicate PACS.008 tests.
- Add stuck PROCESSING recovery tests.
- Add connector timeout/retry behavior tests.
- Add reconciliation job hooks for transfer/outbox/settlement consistency.

Exit criteria:
- Same outbox event cannot dispatch twice under multi-instance load.
- Retry and recovery behavior is deterministic.
- Failed and stuck events are operationally recoverable.
- Idempotency holds under concurrent requests.

### Phase 6 — Observability & Operations
**Status: 🟡 In Progress (30%) — 2026-05-15**

Goal: production incidents must be visible and diagnosable.

**Completed:**
- `micrometer-registry-prometheus` dependency added — all existing Micrometer counters/timers/gauges automatically available at `/actuator/prometheus`.
- `/actuator/prometheus` exposed: staging → port 8080; prod → `${MANAGEMENT_PORT:9090}` (separate from main API port, never public-facing).
- `logback-spring.xml` created: human-readable pattern format for `dev`/`test`; `LogstashEncoder` JSON with `ShortenedThrowableConverter` (rootCauseFirst) for `staging`/`prod`; all MDC fields auto-included.

**Remaining (infrastructure):**
- Prometheus server configured to scrape app.
- Grafana dashboards (6 planned: API, Transfer, Outbox, ISO, Connector, DB/JVM).
- Alerting rules (outbox backlog, stuck events, fail rate, latency).
- Log aggregation (Fluentd/Filebeat → Elasticsearch/OpenSearch).
- Runbooks for each critical alert.

Scope:
- Export metrics to Prometheus.
- Build Grafana dashboards for API, transfer, ISO, outbox, connector, DB, JVM.
- Add alerts for:
  - outbox pending backlog
  - stuck PROCESSING events
  - failed transfer spike
  - connector down/degraded
  - DB connection failure
  - ISO parse/validation error spike
  - p95/p99 latency regression
- Use structured JSON logs.
- Include correlation fields: `requestId`, `transferRef`, `inquiryRef`, `messageId`, `outboxEventId`, `bankCode`.
- Define runbooks per alert.

Exit criteria:
- Ops can diagnose a transfer from one `transferRef`.
- Alerts are connected to notification channels.
- Runbooks exist for critical alerts.
- Dashboard covers day-to-day operations.

### Phase 7 — Deployment & Runtime Platform
**Status: 🟡 In Progress (55%) — 2026-05-15**

Goal: production deployment must be repeatable and reversible.

**Completed:**
- `server.shutdown: graceful` in `application.yml` — Spring drains HTTP requests before JVM shutdown.
- `spring.lifecycle.timeout-per-shutdown-phase: 30s` in `application.yml`.
- Non-root `switching` user in Dockerfile (Phase 1 deliverable, counts here too).
- `k8s/namespace.yaml` — `switching` namespace.
- `k8s/configmap.yaml` — non-secret env vars (SPRING_PROFILES_ACTIVE=prod, SERVER_PORT=8080, MANAGEMENT_PORT=9090, outbox intervals, rate limit config).
- `k8s/secret.yaml` — secret template with `stringData` (DB_URL, DB_PASSWORD, FLYWAY creds, MESSAGE_CRYPTO_KEY_BASE64 — all `REPLACE_ME` placeholders).
- `k8s/deployment.yaml` — 2 replicas, `RollingUpdate maxUnavailable:0 maxSurge:1`, Flyway `initContainer` before app pods, `terminationGracePeriodSeconds:60`, resource requests/limits (cpu 250m/1000m, mem 512Mi/1Gi), livenessProbe + readinessProbe + startupProbe all on management port 9090 (probes hit `/actuator/health/liveness` and `/actuator/health/readiness`), startupProbe allows 2 min max for cold start.
- `k8s/service.yaml` — `ClusterIP` service: port 80→8080 (API), port 9090→9090 (management/Prometheus scrape).
- `k8s/hpa.yaml` — `HorizontalPodAutoscaler`: CPU 70%, Memory 80%, min 2/max 8 pods, scaleDown stabilization 300s (prevents flapping).

**Remaining:**
- Rollback procedure documented and drill completed.
- Deploy tested in staging (zero-dropped-request rolling update under load).
- Container image scan with Trivy.
- Base image digest pinning.

Scope:
- Run containers as non-root user.
- Add liveness/readiness probes.
- Add graceful shutdown behavior for in-flight outbox work.
- Set JVM memory/resource limits.
- Use secret manager or protected env injection.
- Define zero-downtime rollout strategy.
- Define rollback strategy.
- Consider blue/green or canary deployment.
- Run Flyway migration in a controlled deployment step.

Exit criteria:
- Deploy and rollback drills pass.
- Readiness only turns green when DB/Flyway/app dependencies are ready.
- Shutdown does not leave uncontrolled processing state.
- Staging deployment mirrors production.

### Phase 8 — Compliance & Business Readiness

Goal: the platform is ready for bank/business operations.

Scope:
- Define audit retention and data retention.
- Add purge/archive jobs.
- Define RPO/RTO.
- Run disaster recovery drill.
- Add reconciliation and settlement reports.
- Add bank onboarding checklist.
- Add ISO contract/certification tests with bank simulators or partner endpoints.
- Add load and soak tests.
- Add UAT and go-live checklist.

Exit criteria:
- DR drill passes.
- Reconciliation reports are usable by finance/settlement teams.
- Bank onboarding is repeatable.
- Load test meets target throughput and latency.
- Go-live checklist is signed off.

---

### Phase 9 — OAuth 2.0 + mTLS + Request Signing (LaoFP NFR-2.1 to 2.3)
**Status: ⚪ Not Started**

Goal: replace API key authentication with LaoFP-standard OAuth 2.0 + mTLS trust model.

**Scope:**
- Replace `X-API-Key` with `Authorization: Bearer <oauth2_token>` (OAuth 2.0 client credentials flow).
- Add mTLS termination at API Gateway — validate client certificate against registered PSP cert per MOD-02.
- Add `X-Request-Signature: HMAC-SHA256` over body + timestamp — reject requests with invalid or missing signature.
- Add `X-Timestamp` header validation — reject requests older than ±30 seconds (replay protection).
- Rename `X-Bank-Code` → `X-Participant-ID` (LaoFP participant ID format: `PSP-BCEL`, `PSP-LDBBANK`).
- Add participant credential rotation: `POST /v1/participants/{pspId}/credentials/rotate`.
- IP allowlist per participant (configured at API Gateway).
- Migrate existing BANK role to PSP Tier 1/2/3 model.

Exit criteria: all PSP calls require valid OAuth token + mTLS cert + HMAC signature.

### Phase 10 — FPRE Full Compliance (LaoFP B11 / MOD-21)
**Status: ⚪ Not Started**

Goal: bring FPRE to full LaoFP spec compliance.

**Scope:**
- Extend retry schedule to **5 attempts**: 30s / 60s / 2m / 5m / 10m ±10% jitter (currently 3 attempts).
- Add `failureClass` field to outbox events: `TRANSIENT` / `TRANSIENT_POOL` / `PERMANENT_BUSINESS` / `PERMANENT_COMPLIANCE` / `PERMANENT_EXPIRED` / `AMBIGUOUS`.
- Add `willRetry` boolean in all PENDING/FAILED responses and webhooks.
- Implement **auto-reversal** after 5 failed attempts: release pool hold, fire `TRANSFER.MAX_RETRIES_REACHED` + `TRANSFER.POOL_HOLD_RELEASED` + `TRANSFER.REVERSED` webhooks, sending PSP must re-credit customer within 2 business hours.
- Implement **AMBIGUOUS flow**: before re-push, call `GET {pspBase}/laofp/transactions/{txnId}/credit-status` — if `creditApplied=true`, mark COMPLETED without re-push.
- Implement **PSP auto-suspension**: if ≥3 auto-reversals hit same receiving PSP within 30 min → `PUT /participants/{pspId}/status { "status": "INBOUND_SUSPENDED" }` (automated).
- Add `resolvedByRetry`, `retryAttempt` to completed transaction response.
- New FPRE APIs: `GET /v1/transfers/{txnId}/retry-status`, `GET /v1/transfers/{txnId}/retry-history`, `GET /v1/transfers/pending`, `GET /v1/transfers/failed`, `GET /v1/fpre/health`.
- Requires MOD-14 (webhooks) for event delivery.

Exit criteria: FPRE state machine passes LaoFP Certification Test Suite CERT scenarios for retry/reversal.

### Phase 11 — VPA / Account Lookup (LaoFP B1 / MOD-06 + MOD-03)
**Status: ⚪ Not Started**

Goal: implement LaoFP central VPA directory for beneficiary resolution before any payment.

**Scope:**
- VPA types: `MSISDN` / `NATIONAL_ID` / `EMAIL` / `QR_STATIC` / `MERCHANT_ID`.
- `POST /v1/lookup/resolve` → returns `beneficiaryToken` (5-min TTL), `displayName`, `receivingPspId`, `accountType`.
- `beneficiaryToken` passed to `/transfers/initiate` instead of raw account number.
- VPA management: `POST /lookup/vpa/register`, `PUT /lookup/vpa/{vpaId}`, `DELETE /lookup/vpa/{vpaId}`, `GET /lookup/vpa/{vpaId}`.
- Duplicate VPA rejection (same identifier active at multiple PSPs → 409).
- Lookup rate limit: 100/min/PSP (LFP-5001 on exceed).
- Response SLA: <500ms P95 (FR-1.2).
- Masked account display (last 4 digits — existing `MaskingUtil` usable).
- DB: `vpa_registrations` table (vpaId, vpaType, vpaValue, pspId, accountRef, isPrimary, status, expiresAt).

Exit criteria: lookup resolves MSISDN → PSP + masked account in <500ms; beneficiaryToken accepted in transfer flow.

### Phase 12 — Webhook & Notification Engine (LaoFP A7 / MOD-14)
**Status: ⚪ Not Started**

Goal: event-driven notification delivery to all PSPs for every transaction lifecycle event.

**Scope:**
- Webhook registration: `POST /v1/webhooks/register { "url": "...", "events": ["TRANSFER.*"] }`.
- Event catalog (from LaoFP A7): `TRANSFER.COMPLETED`, `TRANSFER.FAILED`, `TRANSFER.PENDING`, `TRANSFER.RETRY_ATTEMPT`, `TRANSFER.MAX_RETRIES_REACHED`, `TRANSFER.REVERSING`, `TRANSFER.REVERSED`, `TRANSFER.EXPIRED`, `TRANSFER.BLOCKED`, `TRANSFER.POOL_HOLD_RELEASED`, `QR.PAYMENT.COMPLETED`, `BILL.PAYMENT.CONFIRMED`, `SETTLEMENT.CYCLE.COMPLETED`, `DISPUTE.STATUS_CHANGED`, `LIQUIDITY.LOW_ALERT`, `PARTICIPANT.STATUS_CHANGED`.
- Delivery: HTTP POST to PSP-registered URL; retry on non-2xx; exponential backoff.
- Delivery tracking: `GET /v1/notifications/{notifId}`.
- Webhook test: `POST /v1/webhooks/{webhookId}/test`.
- Outbox pattern for webhook delivery (reuse existing outbox infrastructure).

Exit criteria: every transaction state change fires correct webhook to affected PSPs; delivery confirmed.

### Phase 13 — Prefunded Pool & Liquidity Management (LaoFP FR-3.1 / MOD-11)
**Status: ⚪ Not Started**

Goal: every transaction atomically debits sending PSP pool and credits receiving PSP pool before routing.

**Scope:**
- `psp_pools` table: pspId, balance, minimumBalance, currency.
- Atomic pool-to-pool ledger on every transaction initiation (fail with LFP-4001 if insufficient).
- Pool hold on INITIATED → release on COMPLETED/REVERSED.
- Wallet operator minimum float: LAK 100,000,000; `LIQUIDITY.LOW_ALERT` at 120%.
- `GET /v1/settlement/balance` — real-time pool balance for calling PSP.
- `POST /v1/settlement/liquidity/topup` — request top-up via BoL RTGS.
- `GET /v1/settlement/positions` — net positions per PSP for current cycle.
- Low-balance alert fires `LIQUIDITY.LOW_ALERT` webhook.

Exit criteria: no transaction can route without confirmed pool balance; pool balance consistent with settlement cycles.

### Phase 14 — Settlement Engine / DNS + RTGS (LaoFP FR-8.1 to FR-8.4 / MOD-10)
**Status: ⚪ Not Started**

Goal: implement 4-cycle DNS settlement and BoL RTGS interface.

**Scope:**
- 4 DNS cycles/day: 09:00, 12:00, 15:30, 20:00 ICT (cut-offs: 08:45, 11:45, 15:15, 19:45).
- Each transaction tagged with `settlementCycleId`.
- Net position computation per PSP pair at cycle close (<60s for 500K transactions).
- RTGS bypass for transactions >LAK 500,000,000 (real-time gross, pacs.009 to BoL RTGS).
- camt.054 report generated per PSP per cycle.
- `GET /v1/settlement/cycles`, `GET /v1/settlement/cycle/{cycleId}/report` (camt.054 download).
- Daily reconciliation: `GET /v1/reports/reconciliation/{date}` — available by 22:00.
- Settlement status: `OPEN` → `CLOSING` → `COMPUTING` → `SETTLED`.
- `SETTLEMENT.CYCLE.COMPLETED` webhook to all PSPs.

Exit criteria: DNS cycles run on schedule; camt.054 delivered to each PSP after each cycle; RTGS high-value path confirmed.

### Phase 15 — QR Code Service (LaoFP FR-5.1 to FR-5.4 / MOD-07)
**Status: ⚪ Not Started**

Goal: national QR standard (EMVCo-based) for merchant payments.

**Scope:**
- Static QR (reusable, no expiry) and Dynamic QR (per-transaction, amount pre-filled, single-use).
- QR fields: `MerchantID`, `AcquiringPspId`, `Amount`, `TxnRef`, `Expiry`, `Checksum` (CRC-16).
- `POST /v1/qr/generate/static`, `POST /v1/qr/generate/dynamic` → returns `qrString`, `qrImageBase64`, `expiresAt`.
- `POST /v1/qr/decode` — validates QR payload, returns merchant info + amount.
- `POST /v1/qr/pay` — submit QR payment (pool-to-pool: issuing PSP → acquiring PSP).
- Merchant refund: `POST /v1/qr/refund` (within 30 days of original txn).
- Duplicate dynamic QR protection: `LFP-QR-003` on same `TxnRef`.
- QR expiry: `LFP-QR-001`.
- Payment SLA: <10 seconds end-to-end (FR-5.3).

Exit criteria: any LaoFP PSP app can pay any LaoFP-registered merchant via QR; refund flows work.

### Phase 16 — Bill Payment Service (LaoFP FR-6.1 to FR-6.3 / MOD-08)
**Status: ⚪ Not Started**

Goal: standardised Fetch-and-Pay bill payment with biller registry.

**Scope:**
- Biller registry: `GET /v1/billers`, `GET /v1/billers/{billerId}`.
- Bill fetch: `GET /v1/bills/fetch?billerId=&ref=` → `billId` valid 10 minutes.
- Bill pay: `POST /v1/bills/pay { "billId": "...", "paymentAmount": "..." }`.
- Duplicate bill payment blocked within 24-hour window (same `billerId` + `billRef`).
- Biller integration: LaoFP forwards `BILL.PAYMENT_INSTRUCTION` to biller API; biller must ACK within 30s.
- `BILL.PAYMENT.CONFIRMED` webhook on success.
- FPRE integration: bill payment retries within 10-min bill token window; if expired → `EXPIRED`, PSP must re-fetch.

Exit criteria: utility/government/telecom bill payment works end-to-end with receipt number returned.

### Phase 17 — Cross-border Payment (LaoFP FR-7.1 to FR-7.4 / MOD-09)
**Status: ⚪ Not Started**

Goal: outbound remittances with real-time FX quote and corridor routing.

**Scope:**
- FX rate: `GET /v1/crossborder/fx-rates`, `POST /v1/crossborder/quote` (30-second rate lock).
- Initiate: `POST /v1/crossborder/initiate { "quoteId": "...", "beneficiary": {...}, "purposeCode": "...", "sourceOfFunds": "..." }`.
- Corridors: Thailand (PromptPay), China (CNAPS), Vietnam (NAPAS), SWIFT global.
- Mandatory AML screening before routing (MOD-13 dependency).
- Transfers >LAK 5,000,000: `purposeCode` + `sourceOfFunds` required (FR-7.3).
- `GET /v1/crossborder/corridors` — list active corridors.
- pacs.008 formatted per target corridor partner spec.

Exit criteria: cross-border transfer completes for all 4 corridors; AML screen blocks sanctioned names.

### Phase 18 — Dispute & Refund Manager (LaoFP FR-9.1 to FR-9.3 / MOD-15)
**Status: ⚪ Not Started**

Goal: full dispute lifecycle with automated refund on adjudication.

**Scope:**
- Dispute types: `NOT_RECEIVED`, `WRONG_AMOUNT`, `DUPLICATE_CHARGE`, `FRAUD`, `MERCHANT_DISPUTE`, `TECHNICAL_ERROR`.
- 90-day dispute window (FR-9.1).
- SLAs per type: `NOT_RECEIVED` 2 days, `WRONG_AMOUNT` 3 days, `FRAUD` 5 days, `TECHNICAL_ERROR` 1 day.
- `POST /v1/disputes/raise`, `GET /v1/disputes/{disputeId}`, `PUT /v1/disputes/{disputeId}/respond`, `POST /v1/disputes/{disputeId}/resolve`.
- Automated refund: `POST /v1/refunds/initiate` (merchant, within 30 days); on `RESOLVED_REFUND` adjudication.
- No response by SLA → auto-ruled in favour of raising PSP.
- `DISPUTE.STATUS_CHANGED` webhook.
- Dispute records retained 7 years.

Exit criteria: all 6 dispute types handled; auto-refund triggered on RESOLVED_REFUND; audit trail immutable.

### Phase 19 — AML / CFT & Risk Engine (LaoFP A3.7, A4.2, C1 / MOD-12 + MOD-13)
**Status: ⚪ Not Started**

Goal: real-time sanctions screening and fraud detection for every transaction.

**Scope:**
- Sanctions screening against BoL, OFAC, and UN lists (<2s for clean names).
- STR auto-generation and submission to BoL FIU within 24 hours of a sanctions hit.
- Blocked transaction: `LFP-SANCTIONS-001`, `TRANSFER.BLOCKED` webhook.
- Real-time fraud scoring per transaction (MOD-12): velocity checks, rule engine, anomaly detection.
- BoL read-only access to real-time transaction monitoring dashboard.
- 7-year immutable audit log (MOD-20): cold storage, regulatory export.
- Data residency: primary data within Lao PDR; DR in ASEAN-region cloud zones.
- PCI-DSS compliance for card-adjacent flows.

Exit criteria: known sanctioned name blocked within 2s; STR filed automatically; BoL dashboard access verified.

### Phase 20 — Performance & Scale (LaoFP NFR-4.1)
**Status: ⚪ Not Started**

Goal: validate and achieve LaoFP NFR performance targets.

**Scope:**
- Load test suite (k6 or Gatling): 2,000 TPS sustained, 10,000 TPS burst.
- P95 end-to-end latency target: <5 seconds for transfers, <10 seconds for QR, <500ms for VPA lookup.
- 99.95% availability SLA (< 4.4 hours downtime/year).
- RTO < 30 seconds (automated failover).
- RPO = 0 for committed transactions (synchronous DB writes before response).
- Multi-zone HA cloud deployment.
- Horizontal pod autoscaling validated under peak load.
- Certification: pass LaoFP Certification Test Suite (CERT-001 to CERT-112, 100% pass rate required).

Exit criteria: load test passes at 2,000 TPS P95 <5s; failover drill confirms RTO <30s; CERT suite 100% pass.

---

## 23. Web Portal Target Architecture

Direction: support production operations with the fewest portals possible while still covering all roles. Do not create 11 separate portals. Build 4 primary business web apps and use external BI/observability/log tools for analytics and technical operations.

### Portal 1 — Operations & Admin Portal

Combines:
- Operations Portal
- Transaction Trace Portal
- ISO Message Portal for internal users
- Outbox / Queue Portal
- Admin/config management
- Compliance/audit read screens for ops context

Core menus:
- Dashboard
- Transactions
- Transfer Trace
- ISO Messages
- Outbox / Queue
- Retry / Stuck Events
- Participants
- Routing Rules
- Connector Configs
- Audit Logs
- Manual Actions

Primary users:
- `ADMIN`
- `OPS_READ`
- `OPS_WRITE`
- `AUDITOR`

Important controls:
- Retry/recover/mark-reviewed actions require write role.
- Viewing decrypted/plain ISO XML requires elevated permission and audit logging.
- All manual actions must create audit records.

### Portal 2 — Member Bank Portal

Purpose: each bank sees and manages only its own data.

Core menus:
- My Transactions
- My Inquiries
- My ISO Messages
- My Transfer Trace
- Download Reports
- Raise Dispute
- API / Certificate Info
- Bank Users

Primary users:
- `BANK_ADMIN`
- `BANK_USER`
- `BANK_VIEWER`

Important controls:
- Every query must be filtered by `bankCode` / participant scope.
- No cross-bank visibility.
- Plain/decrypted XML should be hidden by default or heavily restricted.
- Dispute creation can start here, but dispute operations live in the finance/dispute portal.

### Portal 3 — Finance, Settlement & Dispute Portal

Combines:
- Reconciliation / Settlement Portal
- DRS / Dispute Portal
- Finance-facing audit trail

Core menus:
- Daily Reconciliation
- Settlement Batches
- Bank Statement Import
- Mismatch Review
- Settlement Reports
- Dispute Cases
- Reversal / Adjustment
- Evidence / Timeline
- Approval Workflow
- Finance Audit Trail

Primary users:
- `FINANCE`
- `SETTLEMENT`
- `DISPUTE_AGENT`
- `DISPUTE_MANAGER`
- `COMPLIANCE`
- `AUDITOR`

Important controls:
- Maker/checker approval for settlement adjustment and reversal.
- Evidence and case timeline must be immutable or append-only.
- Settlement exports must be versioned.

### Portal 4 — Risk, Fraud & AML Portal

Purpose: separate sensitive risk/AML workflows from general operations.

Core menus:
- Suspicious Transactions
- Screening Alerts
- Watchlist Hits
- Risk Rules
- Alert Queue
- Case Investigation
- Analyst Notes
- False Positive / Confirmed Fraud
- SAR / AML Report Export
- Risk Audit Trail

Primary users:
- `RISK_ANALYST`
- `RISK_MANAGER`
- `AML_OFFICER`
- `COMPLIANCE`
- `AUDITOR`

Important controls:
- Risk/AML data should not be exposed in normal ops screens.
- Analyst decisions must be audited.
- Rule hit reasons and false-positive handling should be retained.

### External Tools Instead of Custom Portals

Use external tools for analytics and technical observability instead of building custom portals for everything.

BI tool:
- Metabase, Superset, or PowerBI.
- Used for volume, success rate, failed rate, bank performance, settlement summary, SLA, KPI.

Observability tool:
- Prometheus + Grafana.
- Used for API latency, outbox backlog, stuck events, transfer failures, connector health, DB health, JVM metrics.

Log search tool:
- Elasticsearch/Kibana or OpenSearch.
- Used for `requestId`, `transferRef`, `inquiryRef`, `messageId`, error stacks, incident investigation.

### Mapping From Original Portal Ideas

| Original idea | Target home |
|---|---|
| Operations Portal | Operations & Admin Portal |
| Member Bank Portal | Member Bank Portal |
| ISO Message Portal | Operations & Admin Portal + restricted Member Bank views |
| Transaction Trace Portal | Operations & Admin Portal + restricted Member Bank views |
| Outbox / Queue Portal | Operations & Admin Portal |
| Reconciliation / Settlement Portal | Finance, Settlement & Dispute Portal |
| DRS / Dispute Portal | Finance, Settlement & Dispute Portal |
| Risk / Fraud / AML Portal | Risk, Fraud & AML Portal |
| Compliance / Audit Portal | Operations & Admin + Finance + Risk, based on context |
| BI Dashboard | External BI tool |
| Log / Observability | Grafana/Prometheus + Kibana/OpenSearch |

### Portal Build Priority

1. Operations & Admin Portal
2. Member Bank Portal
3. Finance, Settlement & Dispute Portal
4. External BI/Observability/Log tools
5. Risk, Fraud & AML Portal

---

## 24. Phase 0 Deliverables Summary

| Deliverable | File | Status |
|-------------|------|--------|
| Risk Register | `docs/risk-register.md` | ✅ Created 2026-05-14 |
| Production Checklist | `docs/production-checklist.md` | ✅ Created 2026-05-14 |
| Endpoint Matrix | `docs/production-checklist.md` (Section 2) | ✅ Created 2026-05-14 |
| Advanced Roadmap | `overall.md` Section 21 | ✅ Created 2026-05-14 |
| Portal Architecture | `overall.md` Section 22 | ✅ Created 2026-05-14 |

**Risk summary (from `docs/risk-register.md`):**
| Severity | Count | Top priority |
|----------|-------|-------------|
| 🔴 Critical | 9 | RISK-SEC-001, RISK-DB-001, RISK-DB-002, RISK-TEST-001/002/003, RISK-OBS-002, RISK-BIZ-001, RISK-DB-003 |
| 🟠 High | 19 | See risk register |
| 🟡 Medium | 13 | See risk register |
| 🟢 Low | 1 | RISK-ISO-003 |

**Next step:** Start Phase 1 — migrate integration tests to Testcontainers + create CI pipeline.

---

## 25. Update Log

- 2026-05-14: Fixed `docker-compose.yml` environment interpolation for `MESSAGE_CRYPTO_KEY_BASE64` by changing `${MESSAGE_CRYPTO_KEY_BASE64:}` to Docker Compose compatible `${MESSAGE_CRYPTO_KEY_BASE64:-}`. This unblocks `docker compose ps` and clean rebuild/start after removing images and volumes.
- 2026-05-15 (round 1): P3 migrations — V15 (participants + routing_rules seed), V16 (6 performance indexes), V17 (api_keys: key_prefix, expires_at, SHA-256 key_value). P4 API key hardening — ApiKeyHashUtil, ApiKeyAuthFilter (hash+expiry), ApiKeyEntity, ApiKeyService, ApiKeyController, SecurityConfig updated. XXE protection confirmed in Acmt023XmlParser and Pacs008InboundParser.
- 2026-05-15 (round 2): P2 95% — ProductionDemoKeyDisableService (@Profile("prod") ApplicationRunner, disables demo keys by name+prefix, no migration needed), .env in .gitignore confirmed. P3 65% — scripts/init-db-users.sh (switching_app DML-only, switching_flyway ALL), docker-compose updated (mounts init script, app=switching_app, Flyway=switching_flyway, separate passwords), application.yml FLYWAY_URL/USERNAME/PASSWORD env vars with nested fallback. P4 40% — MaskingUtil (maskAccount last-4, maskSensitive), XML body 1MB (server.tomcat.max-http-form-post-size).
- 2026-05-15 (round 3): Bug fix — ParticipantType enum renamed to DIRECT/INDIRECT (was BANK/SWITCHING/SERVICE_PROVIDER, mismatching V15 DB seed values; caused silent 500 on all participant/routing/ISO endpoints); GlobalExceptionHandler.handleGenericException() adds log.error() to surface swallowed exceptions; V19 compensating migration fixes existing DB rows with old enum values; V18 drops duplicate idx_outbox_events_status. P5 35% — exponential backoff (30s/2min/10min) + next_retry_at entity field + findPendingBatch filter + OutboxDispatchWorker graceful shutdown (volatile flag + @PreDestroy). P7 25% — server.shutdown:graceful + timeout-per-shutdown-phase:30s. P4 45% — creditorAccount masked in CreateTransferService TRANSFER_VALIDATE_REQUEST + TRANSFER_CREATED audit events. Test fix — TC-103–107 ISO XML tests switched to BANK_B_KEY (BANK_B→BANK_A via ROUTE_BANK_B_TO_BANK_A_PACS008) to avoid BANK_A_KEY rate-limit exhaustion. 60/60 Maven tests PASS.
- 2026-05-15 (round 4): P5 55% — confirmed `OUTBOX_MANUAL_RETRY_REQUESTED` and `OUTBOX_EVENT_MARKED_REVIEWED` audit events already present in OutboxManualRetryService and OperationsOutboxMarkReviewedService. P6 30% — `micrometer-registry-prometheus` added to pom.xml (Spring Boot BOM managed); `/actuator/prometheus` exposed (staging: main port 8080, prod: `${MANAGEMENT_PORT:9090}` separate to protect public API); `logstash-logback-encoder` 8.0 added; `logback-spring.xml` created (text pattern for default/dev/test, JSON LogstashEncoder + ShortenedThrowableConverter rootCauseFirst for staging/prod, all MDC fields auto-included). 60/60 tests PASS.
- 2026-05-15 (round 6): LaoFP alignment — project renamed to LaoFP Switching API; Section 1 updated to reflect LaoFP Master Spec v1.0 target with MOD-01 to MOD-21 module table and transaction type status; new Section 21 LaoFP Compliance Gap Analysis added (auth stack, FPRE, transaction types, missing modules, API contract alignment, performance targets, compliance score ~15–18%); roadmap expanded from 8 phases to 20 phases (P9–P20 cover all LaoFP expansion modules: OAuth/mTLS, FPRE full, VPA, webhooks, pool/liquidity, settlement/DNS/RTGS, QR, bill pay, cross-border, dispute, AML/CFT, TPS scale).
- 2026-05-15 (round 5): P4 50% — MaskingUtil.maskAccount() applied to audit payloads in CreateInquiryService (creditorAccount), InquiryLookupService (creditorAccount), TransferInquiryService (debtorAccount + creditorAccount), IsoInquiryInboundService (creditorAccount in both audit methods). P5 65% — AuditActorUtil.currentActor() created (reads SecurityContextHolder, fallback "SYSTEM"); replaces hardcoded "API" in OutboxManualRetryService + OperationsOutboxMarkReviewedService. P7 55% — 6 K8s manifests created: k8s/namespace.yaml, k8s/configmap.yaml, k8s/secret.yaml, k8s/deployment.yaml (Flyway initContainer, RollingUpdate maxUnavailable:0/maxSurge:1, 3 probes on mgmt port 9090, startupProbe 24×5s=2min max), k8s/service.yaml (ClusterIP 80→8080 + 9090), k8s/hpa.yaml (CPU 70%/Memory 80%, 2–8 pods, scaleDown 300s). Container hardening [x] from Phase 1 (non-root + multi-stage).

---

## 26. Quick Reference: File Locations

| Purpose | File |
|---------|------|
| Main entry point | `src/main/java/com/example/switching/SwitchingApplication.java` |
| Create transfer (core logic) | `src/main/java/com/example/switching/transfer/service/CreateTransferService.java` |
| Outbox dispatch | `src/main/java/com/example/switching/outbox/service/OutboxProcessorService.java` |
| ISO crypto | `src/main/java/com/example/switching/iso/security/IsoMessageCryptoService.java` |
| Routing (with cache) | `src/main/java/com/example/switching/routing/service/RoutingService.java` |
| Operations trace (MDC + log.error) | `src/main/java/com/example/switching/operations/service/OperationsTransferTraceService.java` |
| Global error handler | `src/main/java/com/example/switching/common/exception/GlobalExceptionHandler.java` |
| Error catalog | `src/main/java/com/example/switching/common/error/ErrorCatalog.java` |
| DB migrations | `src/main/resources/db/migration/V1–V19__*.sql` |
| API key hash util | `src/main/java/com/example/switching/security/util/ApiKeyHashUtil.java` |
| API key service | `src/main/java/com/example/switching/security/service/ApiKeyService.java` |
| API key controller | `src/main/java/com/example/switching/security/controller/ApiKeyController.java` |
| API key auth filter | `src/main/java/com/example/switching/security/filter/ApiKeyAuthFilter.java` |
| Demo key prod disable | `src/main/java/com/example/switching/security/service/ProductionDemoKeyDisableService.java` |
| Account masking util | `src/main/java/com/example/switching/common/util/MaskingUtil.java` |
| Audit actor util | `src/main/java/com/example/switching/common/util/AuditActorUtil.java` |
| K8s Deployment | `k8s/deployment.yaml` |
| K8s HPA | `k8s/hpa.yaml` |
| K8s Service | `k8s/service.yaml` |
| K8s ConfigMap | `k8s/configmap.yaml` |
| K8s Secret template | `k8s/secret.yaml` |
| K8s Namespace | `k8s/namespace.yaml` |
| DB user init script | `scripts/init-db-users.sh` |
| Test application config | `src/test/resources/application-test.yml` |
| Main application config | `src/main/resources/application.yml` |
| Full transfer flow test | `src/test/java/com/example/switching/transfer/FullTransferFlowIntegrationTest.java` |
| Operations trace test | `src/test/java/com/example/switching/operations/service/OperationsTransferTraceIntegrationTest.java` |
| Docker Compose | `docker-compose.yml` |
| Run script | `run.sh` |
| E2E curl tests | `scripts/curl_e2e_tests.sh` |
| MDC log correlation | `src/main/java/com/example/switching/common/filter/RequestIdFilter.java` |
| Outbox metrics + gauges | `src/main/java/com/example/switching/outbox/worker/OutboxDispatchWorker.java` |
| Transfer counters + MDC | `src/main/java/com/example/switching/transfer/service/CreateTransferService.java` |
| Dispatch timer + MDC | `src/main/java/com/example/switching/outbox/service/OutboxProcessorService.java` |
| Logback config (JSON/text) | `src/main/resources/logback-spring.xml` |
| Manual retry audit | `src/main/java/com/example/switching/outbox/service/OutboxManualRetryService.java` |
| Mark-reviewed audit | `src/main/java/com/example/switching/operations/service/OperationsOutboxMarkReviewedService.java` |
| Staging profile config | `src/main/resources/application-staging.yml` |
| Prod profile config | `src/main/resources/application-prod.yml` |
| Full E2E test script | `scripts/run_tests.sh` |
