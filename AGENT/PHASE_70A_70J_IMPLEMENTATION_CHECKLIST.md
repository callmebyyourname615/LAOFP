# Phase 70A–70J Implementation Checklist

> Scope owner: this chatroom only. Additive paths use `phase70`; edits to existing files are limited to the named P0 blockers and integration points below.

## 70A — Webhook ObjectMapper context closure
- [x] Use `ObjectProvider<ObjectMapper>` with an internal fallback for isolated webhook encryption contexts.
- [x] Preserve the application-wide `JacksonConfig` bean when present.
- [x] Add a regression test that starts the webhook encryption configuration without `JacksonConfig`.

## 70B — Operations route-generation FK-safe test isolation
- [x] Remove destructive deletion of connector-less participants from the integration test.
- [x] Restrict route candidates to active participants that already have an enabled connector.
- [x] Add assertions/static guard preventing participant deletion from returning.

## 70C — Cross-border timestamptz binding
- [x] Add a PostgreSQL `Instant` binder using `Types.TIMESTAMP_WITH_TIMEZONE`.
- [x] Use the binder in cross-border rail journal inserts.
- [x] Add a unit regression test that verifies the explicit JDBC type.

## 70D — Per-participant token-bucket rate limiting
- [x] Resolve authenticated participant identity without storing plaintext API keys.
- [x] Maintain independent token buckets per participant/client identity.
- [x] Exclude non-mutating requests from quota consumption.
- [x] Add concurrency-safe unit tests.

## 70E — Participant quota policy and runtime reload
- [x] Add a versioned external policy file with default and participant overrides.
- [x] Validate policy bounds and reject invalid reloads without replacing the last good policy.
- [x] Reload policy on a configurable schedule.
- [x] Rebuild buckets when policy revision or quota changes.

## 70F — Standards-compliant 429 response and audit evidence
- [x] Return `429 Too Many Requests` with `Retry-After`.
- [x] Return limit, remaining-token, and policy-revision headers.
- [x] Emit a structured audit event without exposing credentials.
- [x] Add filter-level tests for allow and reject decisions.

## 70G — Promotion budget concurrency certification
- [x] Strengthen concurrent reservation test with synchronized start and timeout bounds.
- [x] Assert accepted value, reserved value, consumed value, and cap remain consistent.
- [x] Add a static certification script for the concurrency contract.

## 70H — Promotion funder-ledger reconciliation report
- [x] Add reconciliation DTO and service grouped by promotion/funder/currency.
- [x] Detect reservation, consumption, application, and settlement mismatches.
- [x] Add an operations endpoint protected by existing operations RBAC.
- [x] Add integration/unit coverage for balanced and mismatched ledgers.

## 70I — Read-your-writes and stale-replica protection
- [x] Add a replica freshness probe with configurable maximum lag.
- [x] Add a consistency-aware reporting JDBC selector that fails over to primary.
- [x] Force primary for strict/read-your-writes operations.
- [x] Document the consistency policy and test selection decisions.

## 70J — Verification and evidence gate
- [x] Add `scripts/phase70/run_phase70.sh` orchestrator.
- [x] Add static verifier and targeted test runner.
- [x] Add JSON result schema and CI workflow.
- [x] Run shell/Python/JSON/YAML validation.
- [~] Static verification passed; Maven unit tests are blocked by Maven 3.9.12 bootstrap access in this sandbox.
- [x] Attempt Maven compile and targeted tests; record environment blockers honestly.
- [x] Generate changed-files manifest and implementation/validation reports.
- [x] Package only Phase 70 changed files.

## Protected paths
- [x] Confirm no changes under `scripts/phase65/**` through `scripts/phase69/**`.
- [x] Confirm no new Flyway migration was added.
- [x] Confirm no Phase 68/69 file is included in the delivery ZIP.
