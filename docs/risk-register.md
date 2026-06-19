# Switching API — Risk Register

> **Phase 0 — Production Baseline Freeze**
> Created: 2026-05-14
> Last updated: 2026-05-18 — synced with `production-checklist.md` v2.2 (82/82 tests PASS)
> Owner: Platform / Backend Team
> Review cycle: Every sprint (2 weeks)

---

## How to Read This Document

| Field | Meaning |
|-------|---------|
| **ID** | Unique risk identifier (format: `RISK-{DOMAIN}-{NNN}`) |
| **Severity** | 🔴 Critical · 🟠 High · 🟡 Medium · 🟢 Low |
| **Likelihood** | Probability of occurring: High / Medium / Low |
| **Impact** | Business consequence if it materializes |
| **Status** | `OPEN` · `MITIGATED` · `ACCEPTED` · `RESOLVED` |
| **Phase** | Which roadmap phase addresses this risk |

---

## Summary Table

| ID | Domain | Title | Severity | Likelihood | Status | Phase |
|----|--------|-------|----------|------------|--------|-------|
| RISK-SEC-001 | Security | API keys stored in plaintext | 🔴 Critical | High | RESOLVED | P4 |
| RISK-SEC-002 | Security | Demo API keys in production path | 🔴 Critical | High | MITIGATED | P2 |
| RISK-SEC-003 | Security | AES/GCM dev fallback key possible | 🟠 High | Medium | MITIGATED | P1 |
| RISK-SEC-004 | Security | No mTLS on bank-facing ISO endpoints | 🟠 High | Medium | OPEN | P4 |
| RISK-SEC-005 | Security | XML XXE attack surface | 🟠 High | Low | MITIGATED | P4 |
| RISK-SEC-006 | Security | Account numbers in plaintext logs | 🟠 High | High | MITIGATED | P4 |
| RISK-SEC-007 | Security | No IP allowlist per bank | 🟡 Medium | Medium | OPEN | P4 |
| RISK-SEC-008 | Security | No API key expiry or rotation | 🟡 Medium | Low | RESOLVED | P4 |
| RISK-DB-001 | Database | App runs as MySQL root user | 🔴 Critical | High | MITIGATED | P3 |
| RISK-DB-002 | Database | DB connections use useSSL=false | 🔴 Critical | High | OPEN | P3 |
| RISK-DB-003 | Database | No automated DB backup procedure | 🔴 Critical | Medium | OPEN | P3 |
| RISK-DB-004 | Database | V8 migration drops routing_rules | 🟠 High | High | RESOLVED | P3 |
| RISK-DB-005 | Database | No participants seeded in migrations | 🟠 High | High | RESOLVED | P3 |
| RISK-DB-006 | Database | No DB index for outbox polling | 🟡 Medium | Medium | RESOLVED | P3 |
| RISK-DB-007 | Database | No point-in-time recovery configured | 🟠 High | Low | OPEN | P3 |
| RISK-TEST-001 | Test/CI | Tests fail on clean checkout | 🔴 Critical | High | RESOLVED | P1 |
| RISK-TEST-002 | Test/CI | Docker image builds skipping tests | 🔴 Critical | High | MITIGATED | P1 |
| RISK-TEST-003 | Test/CI | No CI pipeline exists | 🔴 Critical | High | RESOLVED | P1 |
| RISK-TEST-004 | Test/CI | Integration tests use MySQL root | 🟠 High | High | RESOLVED | P1 |
| RISK-TEST-005 | Test/CI | Test data accumulates across runs | 🟡 Medium | High | OPEN | P1 |
| RISK-OUT-001 | Outbox | Multi-instance duplicate dispatch not tested | 🟠 High | Medium | RESOLVED | P5 |
| RISK-OUT-002 | Outbox | No exponential backoff on retry | 🟡 Medium | High | RESOLVED | P5 |
| RISK-OUT-003 | Outbox | Stuck PROCESSING has 2-min blind spot | 🟡 Medium | Medium | MITIGATED | P5 |
| RISK-OUT-004 | Outbox | Manual retry has no audit trail | 🟡 Medium | High | RESOLVED | P5 |
| RISK-OUT-005 | Outbox | next_retry_at column not consistently used | 🟡 Medium | Medium | RESOLVED | P5 |
| RISK-OBS-001 | Observability | No Prometheus/Grafana integration | 🟠 High | High | MITIGATED | P6 |
| RISK-OBS-002 | Observability | No alerts configured | 🔴 Critical | High | OPEN | P6 |
| RISK-OBS-003 | Observability | Logs are not structured JSON | 🟠 High | High | RESOLVED | P6 |
| RISK-OBS-004 | Observability | No log aggregation (ELK/OpenSearch) | 🟠 High | High | OPEN | P6 |
| RISK-OBS-005 | Observability | Runbooks do not exist | 🟠 High | High | RESOLVED | P6 |
| RISK-DEP-001 | Deployment | No K8s / container orchestration | 🟠 High | High | MITIGATED | P7 |
| RISK-DEP-002 | Deployment | App runs as root inside container | 🟠 High | Medium | RESOLVED | P7 |
| RISK-DEP-003 | Deployment | No liveness/readiness probes | 🟠 High | High | RESOLVED | P7 |
| RISK-DEP-004 | Deployment | No graceful shutdown for outbox | 🟡 Medium | Medium | RESOLVED | P7 |
| RISK-DEP-005 | Deployment | No rollback procedure documented | 🟠 High | Medium | RESOLVED | P7 |
| RISK-BIZ-001 | Business | No reconciliation/settlement job | 🔴 Critical | High | OPEN | P8 |
| RISK-BIZ-002 | Business | No dispute/reversal mechanism | 🟠 High | Medium | OPEN | P8 |
| RISK-BIZ-003 | Business | No data retention/purge policy | 🟠 High | Low | OPEN | P8 |
| RISK-BIZ-004 | Business | No DR drill or RPO/RTO defined | 🟠 High | Medium | OPEN | P8 |
| RISK-BIZ-005 | Business | No load/soak test results | 🟠 High | High | OPEN | P8 |
| RISK-CFG-001 | Config | Prod can start with empty crypto key (dev fallback) | 🔴 Critical | Low | MITIGATED | P2 |
| RISK-CFG-002 | Config | No secret manager integration | 🟠 High | High | OPEN | P2 |
| RISK-CFG-003 | Config | No staging profile that mirrors prod | 🟡 Medium | Medium | RESOLVED | P2 |
| RISK-ISO-001 | ISO 20022 | No ISO XML size limit | 🟠 High | Low | RESOLVED | P4 |
| RISK-ISO-002 | ISO 20022 | Inquiry TTL not enforced at transfer | 🟡 Medium | Medium | RESOLVED | P5 |
| RISK-ISO-003 | ISO 20022 | inquiry_status_history not used for ISO path | 🟢 Low | High | RESOLVED | P5 |

---

## Detailed Risk Entries

---

### SECURITY RISKS

---

#### RISK-SEC-001 — API Keys Stored in Plaintext
| | |
|---|---|
| **Severity** | 🔴 Critical |
| **Likelihood** | High |
| **Status** | RESOLVED |
| **Roadmap Phase** | Phase 4 |

**Description:**
`api_keys.key_value` stores raw API key strings (e.g. `sk-admin-switching-2026`) directly in the database. Any database read access — including by a compromised DB user, a SQL injection, or a backup leak — exposes all active keys immediately.

**Location:**
- `src/main/resources/db/migration/V14__create_api_keys.sql`
- `src/main/java/com/example/switching/security/repository/ApiKeyRepository.java` → `findByKeyValueAndEnabledTrue()`

**Impact:**
Full API compromise. Attacker can impersonate any role (ADMIN, BANK, OPS) without limit.

**Mitigation Applied:**
V17 hardens API keys: `api_keys.key_value` stores the SHA-256 hex digest, `key_prefix` is stored only for display, and `expires_at` is enforced by `ApiKeyAuthFilter`. `ApiKeyService` returns plaintext only once on create/rotate. `ApiKeyRotationIntegrationTest` TC-KR-001..004 verifies the old key hash is unusable immediately after rotation.

**Residual risk:**
Use an external secrets manager for operational key distribution and complete an end-to-end `/api/admin/api-keys/**` test with a real ADMIN key before production sign-off.

---

#### RISK-SEC-002 — Demo API Keys Remain in Production Path
| | |
|---|---|
| **Severity** | 🔴 Critical |
| **Likelihood** | High |
| **Status** | MITIGATED |
| **Roadmap Phase** | Phase 2 |

**Description:**
V14 migration seeds four demo keys into the `api_keys` table with well-known values (`sk-admin-switching-2026`, `sk-ops-switching-2026`, etc.). These run in every environment including production unless explicitly removed or disabled.

**Location:**
- `src/main/resources/db/migration/V14__create_api_keys.sql`

**Impact:**
Anyone who reads this repository (public or leaked) can authenticate as ADMIN or BANK on production systems using the seeded keys.

**Mitigation Applied:**
`ProductionDemoKeyDisableService` runs under the `prod` profile and disables the seeded demo keys by name/prefix at startup. Production operators must provision a real ADMIN key as part of bootstrap.

**Residual risk:**
Bootstrap still needs a formally approved procedure, preferably backed by a secrets manager or controlled one-time DB bootstrap, so first-production key creation is auditable.

---

#### RISK-SEC-003 — AES/GCM Dev Fallback Key
| | |
|---|---|
| **Severity** | 🟠 High |
| **Likelihood** | Low |
| **Status** | MITIGATED |
| **Roadmap Phase** | Phase 1 (done) |

**Description:**
`IsoMessageCryptoService.resolveKey()` throws `IllegalStateException` at startup if `MESSAGE_CRYPTO_KEY_BASE64` is blank and the active profile is not `test`. The test profile uses a fixed dev key to allow tests to run without the env var.

**Mitigation Applied:**
Startup guard already exists in `IsoMessageCryptoService`. Production cannot start without a key. Test profile uses a distinct fixed key. Risk is low as long as the guard is not removed.

**Residual risk:**
If someone adds `MESSAGE_CRYPTO_KEY_BASE64:` (empty default) back to `application.yml`, the guard is bypassed. Monitor this in code review.

---

#### RISK-SEC-004 — No mTLS on Bank-Facing ISO Endpoints
| | |
|---|---|
| **Severity** | 🟠 High |
| **Likelihood** | Medium |
| **Status** | OPEN |
| **Roadmap Phase** | Phase 4 |

**Description:**
`POST /api/iso20022/pacs008` and `POST /api/iso20022/acmt023` accept connections over TLS (one-way) with only an `X-API-Key` + `X-Bank-Code` header for authentication. Mutual TLS (mTLS) — where the bank must present a client certificate — is not implemented.

**Impact:**
If an API key is leaked, a third party can submit ISO messages as any bank. For financial messaging, mTLS is a standard bank-to-bank trust requirement.

**Mitigation Plan:**
1. Configure Spring Boot `server.ssl.client-auth: need` for ISO endpoints.
2. Issue per-bank client certificates, pinned to `bank_code`.
3. Validate `CN` or `SAN` in the client certificate matches the `X-Bank-Code` header.
4. Store certificate fingerprints in `connector_configs` or a new `bank_certificates` table.

---

#### RISK-SEC-005 — XML XXE Attack Surface
| | |
|---|---|
| **Severity** | 🟠 High |
| **Likelihood** | Low |
| **Status** | MITIGATED |
| **Roadmap Phase** | Phase 4 |

**Description:**
XML parsers (`Acmt023XmlParser`, `Pacs008InboundParser`) use `DocumentBuilderFactory`. If external entity processing is not explicitly disabled, a malicious bank could submit an XML payload that reads local files or makes internal HTTP requests (XXE / SSRF).

**Location:**
- `src/main/java/com/example/switching/iso/inquiry/Acmt023XmlParser.java`
- `src/main/java/com/example/switching/iso/inbound/Pacs008InboundParser.java`

**Impact:**
File disclosure (private keys, application.yml, /etc/passwd). Internal network scanning. Potential remote code execution in worst case.

**Mitigation Applied:**
`Acmt023XmlParser` and `Pacs008InboundParser` now use secure XML parser settings:
```java
DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
dbf.setXIncludeAware(false);
dbf.setExpandEntityReferences(false);
```
The application also sets a 1MB XML/body size limit in Spring Boot config.

**Residual risk:**
Run a dedicated XXE penetration test in staging before production sign-off.

---

#### RISK-SEC-006 — Account Numbers in Plaintext Logs
| | |
|---|---|
| **Severity** | 🟠 High |
| **Likelihood** | High |
| **Status** | MITIGATED |
| **Roadmap Phase** | Phase 4 |

**Description:**
Transfer creation, inquiry processing, and ISO message parsing log full account numbers, bank codes, amounts, and currency in plaintext via SLF4J. These appear in application logs, which may be shipped to ELK/OpenSearch where a broader audience has access.

**Impact:**
Violates PCI DSS / local financial data protection requirements. Account numbers are PII and must be masked in all logs.

**Mitigation Applied:**
`MaskingUtil.maskAccount(String)` is applied to transfer, inquiry, lookup, and ISO inquiry audit payloads. `MaskingUtil.maskXmlAccounts(String)` masks `<DbtrAcct>` and `<CdtrAcct>` account identifiers in ISO XML debug logs, and outbox processor logs were verified to avoid raw account numbers.

**Residual risk:**
Ops portal/API views still need final verification that full account numbers are not visible without elevated permission.

---

#### RISK-SEC-007 — No IP Allowlist per Bank
| | |
|---|---|
| **Severity** | 🟡 Medium |
| **Likelihood** | Medium |
| **Status** | OPEN |
| **Roadmap Phase** | Phase 4 |

**Description:**
Any IP address can attempt to authenticate as BANK_A with a stolen API key. There is no IP-based restriction on which source IPs are allowed to call the ISO endpoints per bank participant.

**Mitigation Plan:**
Add optional `allowed_ip_ranges` column to `api_keys` or `participants`. Enforce in `ApiKeyAuthFilter` or a separate `IpAllowlistFilter` using `X-Forwarded-For` (validated from trusted proxy).

---

#### RISK-SEC-008 — No API Key Expiry or Rotation
| | |
|---|---|
| **Severity** | 🟡 Medium |
| **Likelihood** | Low |
| **Status** | RESOLVED |
| **Roadmap Phase** | Phase 4 |

**Description:**
API keys previously had no `expires_at` field and no rotation endpoint. Once created, a key was valid indefinitely unless manually disabled.

**Mitigation Applied:**
V17 adds `expires_at` and `key_prefix`. `ApiKeyAuthFilter` rejects expired keys, and `POST /api/admin/api-keys/{id}/rotate` rotates a key and immediately invalidates the old hash. `ApiKeyRotationIntegrationTest` verifies old-key rejection after rotation.

---

### DATABASE RISKS

---

#### RISK-DB-001 — Application Runs as MySQL Root
| | |
|---|---|
| **Severity** | 🔴 Critical |
| **Likelihood** | High |
| **Status** | MITIGATED |
| **Roadmap Phase** | Phase 3 |

**Description:**
Earlier local/default configuration connected as MySQL `root`. Root can `DROP DATABASE`, `DROP TABLE`, `CREATE USER`, and perform any operation on any schema.

**Impact:**
Total data loss. Schema destruction. Privilege escalation if MySQL instance is shared.

**Mitigation Applied:**
`scripts/init-db-users.sh` defines separate runtime and migration users, Docker Compose mounts it into MySQL init, and the app now uses `switching_app` while Flyway uses `switching_flyway`:
```sql
CREATE USER 'switching_app'@'%' IDENTIFIED BY '<strong-password>';
GRANT SELECT, INSERT, UPDATE, DELETE ON switching_db.* TO 'switching_app'@'%';
-- Flyway needs CREATE TABLE for migrations — use a separate migration user
CREATE USER 'switching_flyway'@'%' IDENTIFIED BY '<strong-password>';
GRANT ALL PRIVILEGES ON switching_db.* TO 'switching_flyway'@'%';
-- switching_flyway used only during migration, not during normal app operation
```

**Residual risk:**
Production still needs a post-deploy verification that `switching_app` cannot `DROP`/`ALTER`, and the initial MySQL root password must be rotated after migration user setup.

---

#### RISK-DB-002 — DB Connections Use useSSL=false
| | |
|---|---|
| **Severity** | 🔴 Critical |
| **Likelihood** | High |
| **Status** | OPEN |
| **Roadmap Phase** | Phase 3 |

**Description:**
Default JDBC URL: `jdbc:mysql://localhost:3306/switching_db?useSSL=false&allowPublicKeyRetrieval=true`. All DB traffic (including transfer data, ISO messages, account numbers) is transmitted unencrypted.

**Impact:**
Man-in-the-middle attack on DB traffic. Data exposure on any shared network.

**Mitigation Plan:**
```
# Prod DB URL:
jdbc:mysql://${DB_HOST}:3306/switching_db
  ?useSSL=true
  &requireSSL=true
  &verifyServerCertificate=true
  &trustCertificateKeyStoreUrl=file:/certs/truststore.jks
  &trustCertificateKeyStorePassword=${DB_TRUSTSTORE_PASSWORD}
```
Enable SSL on MySQL server side and issue a server certificate.

---

#### RISK-DB-003 — No Automated DB Backup Procedure
| | |
|---|---|
| **Severity** | 🔴 Critical |
| **Likelihood** | Medium |
| **Status** | OPEN |
| **Roadmap Phase** | Phase 3 |

**Description:**
No documented backup schedule, retention policy, or restore procedure exists. Any disk failure, accidental drop, or corruption will result in permanent data loss.

**Mitigation Plan:**
1. Daily logical backup: `mysqldump --single-transaction switching_db | gzip > backup-$(date +%Y%m%d).sql.gz`
2. Store backups in separate storage (S3, GCS, or off-site NFS).
3. Retention: 30 days rolling.
4. Enable MySQL binary logging for point-in-time recovery: `log_bin = ON`, `expire_logs_days = 7`.
5. Monthly restore drill: restore from backup to a test instance, verify data and Flyway checksum.

---

#### RISK-DB-004 — V8 Migration Drops routing_rules After V2 Seed
| | |
|---|---|
| **Severity** | 🟠 High |
| **Likelihood** | High |
| **Status** | RESOLVED |
| **Roadmap Phase** | Phase 3 |

**Description:**
`V8__create_participants_and_routing_rules.sql` drops and recreates the `routing_rules` table. V2 had seeded `ROUTE_BANK_B_PRIMARY`. After V8 runs on a fresh install, the table is empty and the application cannot route any transfer until routes are manually created.

**Impact:**
Fresh production install has zero routing rules → all transfers fail immediately with `RTE-001 (422)`.

**Mitigation Applied:**
V15 seeds the baseline BANK_A/BANK_B/BANK_C routing rules idempotently with `ON DUPLICATE KEY UPDATE`, so a fresh install has working routes after migrations.

---

#### RISK-DB-005 — No Participants Seeded in Migrations
| | |
|---|---|
| **Severity** | 🟠 High |
| **Likelihood** | High |
| **Status** | RESOLVED |
| **Roadmap Phase** | Phase 3 |

**Description:**
The `participants` table (added in V8) is never seeded by any migration. On a fresh install, `POST /api/inquiries` or `POST /api/iso20022/pacs008` will fail with `PRT-001 (404)` for any bank code.

**Impact:**
Production deploy requires manual onboarding before any transaction can be processed.

**Mitigation Applied:**
V15 seeds BANK_A, BANK_B, and BANK_C participants idempotently. The operations bank-onboarding API remains available for additional banks.

---

#### RISK-DB-006 — Missing Indexes for Operational Queries
| | |
|---|---|
| **Severity** | 🟡 Medium |
| **Likelihood** | Medium |
| **Status** | RESOLVED |
| **Roadmap Phase** | Phase 3 |

**Description:**
Several high-frequency queries lack appropriate indexes. As transfer volume grows (millions of rows), these will cause full table scans.

**Missing indexes:**
| Table | Query pattern | Recommended index |
|-------|--------------|-------------------|
| `transfers` | `WHERE status = ? ORDER BY created_at DESC` | `(status, created_at DESC)` |
| `outbox_events` | `WHERE status = 'PENDING' ORDER BY created_at` | `(status, next_retry_at)` |
| `outbox_events` | `WHERE status = 'PROCESSING' AND updated_at < ?` | `(status, updated_at)` |
| `audit_logs` | `WHERE reference_id = ?` | `(reference_id, created_at)` |
| `iso_messages` | `WHERE transfer_ref = ?` | `(transfer_ref, direction)` |
| `idempotency_records` | `WHERE expired_at < NOW()` (cleanup job) | `(expired_at)` |

**Mitigation Applied:**
V16 adds the operational indexes listed above. V18 removes the duplicate outbox status index that existed after historical migrations.

---

#### RISK-DB-007 — No Point-in-Time Recovery
| | |
|---|---|
| **Severity** | 🟠 High |
| **Likelihood** | Low |
| **Status** | OPEN |
| **Roadmap Phase** | Phase 3 |

**Description:**
MySQL binary logging is not confirmed enabled. Without binlogs, the maximum data loss window equals the time since the last full backup (potentially 24 hours for daily backups).

**Mitigation Plan:**
Enable in `my.cnf`: `log_bin = /var/log/mysql/mysql-bin.log` and `binlog_format = ROW`. Set `expire_logs_days = 7`. Test recovery using `mysqlbinlog` from a backup point.

---

### TEST & CI RISKS

---

#### RISK-TEST-001 — Integration Tests Fail on Clean Checkout
| | |
|---|---|
| **Severity** | 🔴 Critical |
| **Likelihood** | High |
| **Status** | RESOLVED |
| **Roadmap Phase** | Phase 1 |

**Description:**
Previously, `./mvnw test` required a locally running MySQL instance with root credentials that were not documented or portable. A new developer or CI runner on a clean machine could not run tests without setup drift.

**Impact:**
- Changes cannot be safely verified before merge.
- Bugs introduced by refactoring are not caught by CI.
- Test suite provides false confidence (appears to exist, does not actually gate changes).

**Mitigation Applied:**
Integration tests now extend `AbstractIntegrationTest` with a singleton Testcontainers MySQL and `@DynamicPropertySource`. Latest checklist verification: `./mvnw test` passes on a clean machine with **82/82 tests PASS**.

---

#### RISK-TEST-002 — Docker Image Builds with -DskipTests
| | |
|---|---|
| **Severity** | 🔴 Critical |
| **Likelihood** | High |
| **Status** | MITIGATED |
| **Roadmap Phase** | Phase 1 |

**Description:**
The Dockerfile build stage uses `-DskipTests`, so test enforcement depends on the CI pipeline rather than the image build itself.

**Location:** `Dockerfile` — build stage

**Impact:**
Production can receive a broken image. No safety net between code change and deployment.

**Mitigation Applied:**
The Docker build still skips tests for image-build speed, but CI now gates Docker image build and registry push behind compile, unit, integration, and package jobs:
   ```yaml
   jobs:
     test:
       runs: ./mvnw test
     docker-build:
       needs: test
       runs: docker build ...
   ```

**Residual risk:**
GitHub branch protection still needs to require the CI status checks before merge.

---

#### RISK-TEST-003 — No CI Pipeline Exists
| | |
|---|---|
| **Severity** | 🔴 Critical |
| **Likelihood** | High |
| **Status** | RESOLVED |
| **Roadmap Phase** | Phase 1 |

**Description:**
The repository previously had no CI workflow, so merges depended on manual validation.

**Mitigation Applied:**
`.github/workflows/ci.yml` now runs compile, tests, packaging, Docker image build, Trivy scan, and main-branch registry push:
```
on: [push, pull_request]
jobs:
  1. compile          → mvnw compile -q
  2. unit-test        → mvnw test -Dgroups=unit
  3. integration-test → mvnw test -Dgroups=integration (Testcontainers)
  4. package          → mvnw package -DskipTests (only if #3 passes)
  5. docker-build     → docker build (only if #4 passes)
  6. docker-push      → push to registry (only on main branch)
  7. trivy-scan       → block HIGH/CRITICAL image issues before push
```

---

### OUTBOX RISKS

---

#### RISK-OUT-001 — Multi-Instance Duplicate Dispatch Not Tested
| | |
|---|---|
| **Severity** | 🟠 High |
| **Likelihood** | Medium |
| **Status** | RESOLVED |
| **Roadmap Phase** | Phase 5 |

**Description:**
`OutboxProcessorService` claims events with `PENDING → PROCESSING` in a `PROPAGATION_REQUIRES_NEW` transaction, relying on an atomic database update to prevent two instances from processing the same event.

**Impact:**
Duplicate PACS.008 dispatched to the same destination bank — double payment. Financial loss. This is the highest business impact scenario.

**Mitigation Applied:**
`OutboxEventRepository.claimPendingEvent(id, PENDING, PROCESSING)` uses an atomic `UPDATE WHERE status='PENDING'`. `OutboxConcurrentDispatchIntegrationTest` TC-CC-001/002 verifies two concurrent processors cannot dispatch the same event twice, and no duplicate `OUTBOX_DISPATCH_STARTED` audit entry is created.

---

#### RISK-OUT-002 — No Exponential Backoff on Retry
| | |
|---|---|
| **Severity** | 🟡 Medium |
| **Likelihood** | High |
| **Status** | RESOLVED |
| **Roadmap Phase** | Phase 5 |

**Description:**
Retried outbox events previously returned to `PENDING` with no delay. If a downstream connector was down, all retry attempts could happen in rapid succession, wasting resources and potentially hitting the downstream bank with a burst of failed requests.

**Mitigation Applied:**
`OutboxProcessorService.finalizeTechnicalFailure` now populates `next_retry_at`, and the poller filters future retries. `OutboxBackoffIntegrationTest` verifies events are not reprocessed before `next_retry_at`.
```
Retry 1: +30 seconds
Retry 2: +2 minutes
Retry 3+: +10 minutes
```

---

#### RISK-OUT-004 — Manual Retry Has No Audit Trail
| | |
|---|---|
| **Severity** | 🟡 Medium |
| **Likelihood** | High |
| **Status** | RESOLVED |
| **Roadmap Phase** | Phase 5 |

**Description:**
`POST /api/outbox-events/{id}/retry` allows OPS/ADMIN to manually re-queue a failed outbox event. Earlier versions did not record who triggered the retry, when, or what the event state was at the time.

**Impact:**
Manual intervention must be traceable to an actor for financial operations.

**Mitigation Applied:**
`OutboxManualRetryService` writes `OUTBOX_MANUAL_RETRY_REQUESTED` audit entries, and `OperationsOutboxMarkReviewedService` writes `OUTBOX_EVENT_MARKED_REVIEWED`. Actors come from `AuditActorUtil.currentActor()`.

---

### OBSERVABILITY RISKS

---

#### RISK-OBS-001 — No Prometheus/Grafana Integration
| | |
|---|---|
| **Severity** | 🟠 High |
| **Likelihood** | High |
| **Status** | MITIGATED |
| **Roadmap Phase** | Phase 6 |

**Description:**
Micrometer metrics are registered and exported via `/actuator/metrics`, but there is no Prometheus scrape config or Grafana dashboard. The data exists but no one can see it in real time.

**Mitigation Applied:**
`micrometer-registry-prometheus` is present and `/actuator/prometheus` is exposed. In production it is intended for the management port only.

**Residual risk:**
Prometheus scrape config and Grafana dashboards remain infrastructure tasks.

---

#### RISK-OBS-002 — No Alerts Configured
| | |
|---|---|
| **Severity** | 🔴 Critical |
| **Likelihood** | High |
| **Status** | OPEN |
| **Roadmap Phase** | Phase 6 |

**Description:**
There are no alerting rules. If the outbox builds up 1,000 stuck events, if all transfers start failing, or if the connector goes down, nobody is notified. Issues will be discovered by users, not by the operations team.

**Critical alerts needed (minimum):**
| Alert | Condition | Channel |
|-------|-----------|---------|
| Outbox backlog | `outbox_pending > 100` for 5 min | PagerDuty / Slack |
| Stuck events | `outbox_stuck_processing > 5` for 2 min | PagerDuty |
| Transfer failures spike | failed rate > 10% in 1 min | Slack |
| Connector down | `NET-001/NET-002` errors > 5 in 1 min | PagerDuty |
| API 5xx spike | 500 response rate > 1% | Slack |
| DB connection exhausted | Pool usage > 90% | PagerDuty |

---

#### RISK-OBS-003 — Logs Are Not Structured JSON
| | |
|---|---|
| **Severity** | 🟠 High |
| **Likelihood** | High |
| **Status** | RESOLVED |
| **Roadmap Phase** | Phase 6 |

**Description:**
Current `logback-spring.xml` (or default Logback config) outputs plain text logs. When logs are shipped to ELK/OpenSearch, they cannot be reliably parsed or filtered by `transferRef`, `requestId`, etc., without a Grok parser — which is fragile and error-prone.

**Mitigation Applied:**
`logstash-logback-encoder` is configured in `logback-spring.xml`: text logs for `default/dev/test`, JSON logs for `staging/prod`, and MDC fields such as `requestId`, `transferRef`, `inquiryRef`, `outboxEventId`, and `bankCode` are included automatically.

---

### DEPLOYMENT RISKS

---

#### RISK-DEP-002 — App Runs as Root Inside Container
| | |
|---|---|
| **Severity** | 🟠 High |
| **Likelihood** | Medium |
| **Status** | RESOLVED |
| **Roadmap Phase** | Phase 7 |

**Description:**
Earlier Dockerfile versions did not set a non-root user, so the JVM process ran as UID 0 inside the container.

**Mitigation Applied:**
The Dockerfile now creates and runs as a non-root `switching` user:
```dockerfile
RUN addgroup --system switching && adduser --system --ingroup switching switching
USER switching
```

---

#### RISK-DEP-003 — No Liveness/Readiness Probes
| | |
|---|---|
| **Severity** | 🟠 High |
| **Likelihood** | High |
| **Status** | RESOLVED |
| **Roadmap Phase** | Phase 7 |

**Description:**
`/actuator/health` exists but is not configured as a Kubernetes probe. During a rolling deployment, new pods may receive traffic before Flyway migrations complete and the DB connection pool is established — causing 500 errors.

**Mitigation Applied:**
Kubernetes manifests now define liveness and readiness probes on the management port, and Flyway runs in an initContainer before app pods start:
```yaml
livenessProbe:
  httpGet: { path: /actuator/health/liveness, port: 9090 }
  initialDelaySeconds: 30
  periodSeconds: 10
readinessProbe:
  httpGet: { path: /actuator/health/readiness, port: 9090 }
  initialDelaySeconds: 20
  periodSeconds: 5
```

---

### BUSINESS RISKS

---

#### RISK-BIZ-001 — No Reconciliation or Settlement Job
| | |
|---|---|
| **Severity** | 🔴 Critical |
| **Likelihood** | High |
| **Status** | OPEN |
| **Roadmap Phase** | Phase 8 |

**Description:**
There is no process to compare the switching system's transfer records against the destination bank's settlement records. Discrepancies (transfers marked SUCCESS in switching but not settled at the bank, or vice versa) will accumulate silently.

**Impact:**
Financial losses. Regulatory violations. Inability to detect systemic failures or fraud patterns.

**Mitigation Plan:**
1. Build a daily reconciliation job that compares `transfers WHERE status='SUCCESS' AND DATE(created_at) = yesterday` against bank settlement files.
2. Flag mismatches as `RECONCILIATION_MISMATCH` entries.
3. Route mismatches to Finance/Settlement Portal for human review.

---

#### RISK-BIZ-004 — No DR Drill or RPO/RTO Defined
| | |
|---|---|
| **Severity** | 🟠 High |
| **Likelihood** | Medium |
| **Status** | OPEN |
| **Roadmap Phase** | Phase 8 |

**Description:**
There are no defined Recovery Point Objective (RPO) or Recovery Time Objective (RTO) targets. No DR drill has been performed. If a datacenter failure occurs, recovery time and data loss are unknown.

**Mitigation Plan:**
1. Define RPO: ≤ 1 hour (max data loss acceptable).
2. Define RTO: ≤ 4 hours (max time to restore service).
3. Design backup strategy to meet RPO.
4. Document recovery runbook.
5. Run DR drill quarterly (restore from backup, verify, measure time).

---

#### RISK-BIZ-005 — No Load or Soak Test Results
| | |
|---|---|
| **Severity** | 🟠 High |
| **Likelihood** | High |
| **Status** | OPEN |
| **Roadmap Phase** | Phase 8 |

**Description:**
The application has never been tested under production-like load. There are no benchmarks for:
- Maximum sustainable transfers per minute
- DB connection pool behavior under load
- Outbox worker throughput
- Memory behavior over 24+ hours (soak test)

**Target performance (to be validated):**
| Scenario | Target |
|----------|--------|
| Sustained load | 500 transfers/min |
| Peak burst | 1,000 transfers/min for 1 min |
| p99 API latency | < 500ms |
| Outbox dispatch | < 200ms p95 |
| Soak test | 24 hours at 100 tps, no memory leak |

---

## Risk Heatmap

```
         LIKELIHOOD
         Low        Medium       High
       ┌──────────┬────────────┬────────────────────────────┐
CRIT   │           │DB-003      │DB-002 OBS-002              │
🔴     │           │            │BIZ-001                     │
       │           │            │                            │
├──────┼──────────┼────────────┼────────────────────────────┤
HIGH   │SEC-004    │BIZ-002     │OBS-004 BIZ-005             │
🟠     │DB-007     │BIZ-004     │CFG-002                     │
       │BIZ-003    │            │                            │
       │           │            │                            │
       │           │            │                            │
       │           │            │                            │
├──────┼──────────┼────────────┼────────────────────────────┤
MED    │           │SEC-007     │TEST-005                    │
🟡     │           │            │                            │
       │           │            │                            │
       │           │            │                            │
       │           │            │                            │
├──────┼──────────┼────────────┼────────────────────────────┤
LOW    │           │            │                            │
🟢     │           │            │                            │
       └──────────┴────────────┴────────────────────────────┘
```

Resolved/mitigated risks are intentionally removed from the heatmap so it only shows active production-readiness exposure.

---

## Change Log

| Date | Version | Changes |
|------|---------|---------|
| 2026-05-14 | 1.0 | Initial risk register — Phase 0 baseline freeze |
| 2026-05-18 | 1.1 | Synced statuses with `production-checklist.md` v2.2: API key hardening, demo key mitigation, XXE, masking, Testcontainers/CI, DB seeds/indexes, outbox backoff/concurrency/audit, structured logging, runbooks, K8s probes, graceful shutdown, rollback, ISO TTL/history/size limit |
