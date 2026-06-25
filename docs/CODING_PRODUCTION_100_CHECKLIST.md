# Coding Production 100% Checklist

> Scope: coding-only readiness for the Switching project. This checklist does not cover runtime evidence, UAT execution, operator sign-off, or production cutover activities.

## Status Legend

- `[ ]` Not started
- `[~]` In progress
- `[x]` Done
- `[-]` Not applicable

## Target Definition

Coding is considered production-ready when the repository can prove all of the following from source code, tests, static checks, generated contracts, and CI gates:

```text
python3 scripts/verify_all_static.py     PASS
./mvnw verify                            PASS
API inventory generated + checked in CI  PASS
OpenAPI contract generated               PASS
Security endpoint matrix tests           PASS
Critical write flows idempotency tests   PASS
Flyway migration validation              PASS
Error response contract tests            PASS
No SNAPSHOT/demo metadata                PASS
Repo hygiene/static checks               PASS
```

---

## P0 - Coding Blockers

### P0.1 Static Gates Must Pass

- [ ] Restore or implement `docs/aml/SANCTIONS_PROVIDER_ONBOARDING.md`
- [ ] Restore or implement `docs/security/VAULT_EXTERNAL_SECRETS_RUNBOOK.md`
- [ ] Restore or implement `docs/implementation/PHASES_05_TO_07_IMPLEMENTATION.md`
- [ ] Run `python3 scripts/verify_all_static.py`
- [ ] Add missing static verifier coverage if any phase can drift without failing CI
- [ ] Ensure CI runs static gates on every pull request

**Done when:** `python3 scripts/verify_all_static.py` exits `0` locally and in CI.

### P0.2 Full Maven Verification

- [ ] Run `./mvnw verify`
- [ ] Fix all unit test failures
- [ ] Fix all integration test failures
- [ ] Fix Flyway validation or migration ordering failures
- [ ] Fix JaCoCo/report generation failures
- [ ] Add regression tests for every root-cause fix
- [ ] Ensure CI runs `verify` or an equivalent full verification gate before packaging a release candidate

**Done when:** `./mvnw verify` exits `0` from a clean checkout.

### P0.3 API Inventory as a Maintained Artifact

- [ ] Convert endpoint extraction into a committed script, for example `scripts/generate_api_inventory.py`
- [ ] Generate `API_ENDPOINTS.txt` from source code
- [ ] Generate `API_ENDPOINTS_DARK.html` from `API_ENDPOINTS.txt`
- [ ] Add CI check that fails when controller mappings change but inventory is stale
- [ ] Include non-controller runtime routes such as Actuator, static resources, and OpenAPI/Swagger routes
- [ ] Document resolved path placeholders such as `${switching.api.v1-prefix}` = `/v1`

**Done when:** endpoint inventory is reproducible and drift is caught automatically.

### P0.4 OpenAPI Contract Coverage

- [ ] Confirm SpringDoc/OpenAPI dependency and runtime configuration
- [ ] Generate `docs/openapi/openapi.json`
- [ ] Ensure every public and partner-facing endpoint has request/response schema coverage
- [ ] Add examples for critical payment, settlement, webhook, RTP, QR, bill payment, and cross-border flows
- [ ] Add CI contract diff for pull requests
- [ ] Add backwards-compatibility rules for breaking API changes

**Done when:** OpenAPI generation is automated and breaking changes are visible in CI.

### P0.5 Authorization Matrix

- [ ] Enumerate all 215 application controller endpoints
- [ ] Classify each endpoint by audience: public, bank, ops, admin, system admin, internal
- [ ] Verify every endpoint is covered by `SecurityConfig` or method-level authorization
- [ ] Add tests for unauthenticated access
- [ ] Add tests for wrong-role access
- [ ] Add tests for allowed-role access
- [ ] Verify sensitive endpoints do not fall through to generic authenticated access
- [ ] Verify Actuator and runtime endpoints are restricted as intended

**Done when:** an endpoint authorization matrix test suite passes and covers every route in the API inventory.

---

## P1 - Production Code Quality

### P1.1 Standard Error Contract

- [ ] Define a single error response shape with `errorCode`, `message`, `status`, `path`, `traceId`, and `timestamp`
- [ ] Ensure validation errors use the same envelope
- [ ] Ensure security errors do not leak sensitive details
- [ ] Ensure domain errors have stable error codes
- [ ] Add contract tests for representative controller errors
- [ ] Verify no raw stack traces or raw exception messages are exposed

**Done when:** all controller error paths return a consistent, documented response shape.

### P1.2 Idempotency Coverage

- [ ] Audit `POST /api/transfers`
- [ ] Audit `POST /api/inquiries`
- [ ] Audit `POST /api/iso20022/pacs008`
- [ ] Audit `POST /v1/bills/pay`
- [ ] Audit QR pay/refund endpoints
- [ ] Audit RTP authorize, decline, settle, and cancel endpoints
- [ ] Audit cross-border quote/initiate/inbound endpoints
- [ ] Audit settlement instruction approval/reject/send endpoints
- [ ] Add duplicate request tests
- [ ] Add concurrent request tests
- [ ] Add retry-after-timeout tests where applicable

**Done when:** all critical write flows are either idempotent or explicitly documented as non-idempotent with safe failure behavior.

### P1.3 Transaction Boundary Audit

- [ ] Review services that write ledger, settlement, reconciliation, outbox, sanctions, liquidity, and report-delivery state
- [ ] Ensure business state and outbox writes are atomic where required
- [ ] Ensure external calls are not performed inside long-running DB transactions unless explicitly justified
- [ ] Ensure rollback rules are correct for checked and unchecked exceptions
- [ ] Add integration tests for partial failure and rollback behavior
- [ ] Add concurrency tests for settlement and ledger-sensitive flows

**Done when:** critical data-changing flows have explicit and tested transaction boundaries.

### P1.4 Flyway Migration Hardening

- [ ] Add static migration linter for risky SQL operations
- [ ] Flag `DROP`, destructive `ALTER`, table rewrites, and non-concurrent indexes on large tables
- [ ] Validate migration from an empty database
- [ ] Validate migration from the latest production baseline
- [ ] Validate repeatable migration behavior if repeatable migrations are introduced
- [ ] Add schema compatibility checks for application startup
- [ ] Keep migration count and latest version documented in CI output

**Done when:** migration safety is checked automatically before code can be released.

### P1.5 Observability in Code

- [ ] Add trace IDs to all critical logs
- [ ] Add transaction references to payment, settlement, inquiry, and reconciliation logs
- [ ] Add participant IDs where safe and useful
- [ ] Ensure logs are sanitized for PII, secrets, credentials, account numbers, and tokens
- [ ] Add metrics for success/failure/latency on critical flows
- [ ] Add business metrics for settlement cycle, outbox lag, webhook delivery, sanctions freshness, and reconciliation gaps
- [ ] Add tracing spans around external calls and long-running operations

**Done when:** critical flows can be diagnosed from logs, metrics, and traces without exposing sensitive data.

### P1.6 External Integration Contract Tests

- [ ] Add contract tests for BOL RTGS integration
- [ ] Add contract tests for FIU/sanctions providers
- [ ] Add contract tests for Kafka/outbox queue behavior
- [ ] Add contract tests for object storage/archive behavior
- [ ] Add contract tests for webhook delivery and retry behavior
- [ ] Add contract tests for cross-border rail adapters
- [ ] Use Testcontainers, mock servers, or signed fixtures where appropriate

**Done when:** external protocol assumptions are tested without requiring real production dependencies.

### P1.7 Endpoint Smoke Tests

- [ ] Generate smoke tests from API inventory
- [ ] Verify all documented endpoints route to a handler and do not return `404`
- [ ] Verify unsupported methods return the expected response
- [ ] Verify JSON-only payment initiation blockers behave as configured
- [ ] Verify ISO XML endpoints accept correct content types
- [ ] Verify validation reaches the business boundary for allowed roles

**Done when:** endpoint inventory and running application routing agree.

---

## P2 - Release Candidate Hygiene

### P2.1 Release Metadata Cleanup

- [ ] Replace `0.0.1-SNAPSHOT` with a release candidate version such as `1.0.0-rc.1`
- [ ] Replace demo project description in `pom.xml`
- [ ] Fill useful project metadata where required: name, description, license, SCM
- [ ] Ensure build metadata includes commit SHA and image digest
- [ ] Ensure artifact naming matches release governance

**Done when:** built artifacts no longer look like a demo or development snapshot.

### P2.2 Repository Hygiene

- [ ] Ensure `.DS_Store` and local OS files are ignored
- [ ] Remove tracked local noise files if any are still tracked
- [ ] Add static hygiene check for prohibited files
- [ ] Verify `.env` and `.env.bak` are not tracked
- [ ] Verify committed examples contain placeholders only
- [ ] Verify generated reports and evidence artifacts are either intentionally tracked or ignored

**Done when:** repository hygiene checks pass and local machine artifacts cannot enter source control.

### P2.3 API Consumer Consistency

- [ ] Align SMOS/admin frontend paths with backend API inventory
- [ ] Generate or maintain a typed API client from OpenAPI
- [ ] Remove duplicated hardcoded endpoint paths where practical
- [ ] Add frontend/backend contract checks if a portal is part of the release scope
- [ ] Document deprecated or compatibility aliases such as `/api/dashboard/cross-border` and `/api/dashboard/crossborder`

**Done when:** consumers can rely on a stable, generated, or verified API contract.

### P2.4 Documentation Sync

- [ ] Link `API_ENDPOINTS.txt` and `API_ENDPOINTS_DARK.html` from relevant docs
- [ ] Link OpenAPI output from relevant docs
- [ ] Link authorization matrix from security docs
- [ ] Link error contract from API docs
- [ ] Link idempotency policy from payment/API docs
- [ ] Remove stale phase claims that no longer match source code

**Done when:** coding documentation matches the repository state and generated artifacts.

---

## Suggested Execution Order

1. [ ] Make static gates pass
2. [ ] Make `./mvnw verify` pass
3. [ ] Commit API inventory generator and CI drift check
4. [ ] Generate and gate OpenAPI contract
5. [ ] Build endpoint authorization matrix tests
6. [ ] Harden idempotency and transaction boundaries
7. [ ] Add migration safety checks
8. [ ] Add endpoint smoke tests
9. [ ] Clean release metadata
10. [ ] Final repository hygiene pass

---

## Final Coding Sign-Off

- [ ] Static gates pass locally
- [ ] Static gates pass in CI
- [ ] Maven verify passes locally
- [ ] Maven verify passes in CI
- [ ] API inventory is current
- [ ] OpenAPI contract is current
- [ ] Authorization matrix is complete
- [ ] Critical idempotency tests pass
- [ ] Transaction rollback/concurrency tests pass
- [ ] Migration safety checks pass
- [ ] Error contract tests pass
- [ ] Release metadata is production-grade
- [ ] Repository hygiene checks pass

**Coding production readiness:** `[ ] Not ready` / `[ ] Ready for runtime evidence and release-candidate freeze`
