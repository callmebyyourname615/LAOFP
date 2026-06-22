# Phase II-01 through II-04 Validation Report

Changed-file payload: **55 files**, with no deletions.

## Passed in the delivery environment

- Phase II static contract verifier.
- V85 table/index/constraint contract checks.
- `VARCHAR(64)` SHA-256 policy check.
- Default-off feature-flag checks.
- RTP security-route contract checks.
- Shell syntax for apply and curl scripts.
- Python compilation for static verifiers.
- YAML parsing for application, workflow, and Phase II contracts.
- Java delimiter/literal structural validation for modified and new Java files.
- Isolated Java 21 compilation and execution of the RTP state machine.
- Git whitespace/error check.

## Prepared for CI/UAT execution

- State-machine unit tests.
- Standalone MVC controller/validation tests.
- Testcontainers integration tests for:
  - create/query/cancel lifecycle;
  - same-key/same-payload replay;
  - same-key/different-payload conflict;
  - participant access isolation;
  - six-way concurrent create;
  - V85 Flyway history and SHA-256 column type.

## Not executed here

`./mvnw compile/test` could not run because the delivery environment had no
Maven 3.9.12 cache and could not download it from Maven Central. The GitHub
Actions workflow runs the targeted Maven/Testcontainers suite on an Ubuntu
runner with Java 21.

## Existing baseline findings, not introduced by this delivery

The uploaded baseline's older static gates still report production-hardening
items that are intentionally deferred until feature development is complete:

- Phase 1: migration application does not exclude Kafka auto-configuration.
- Phase 2–4 and Phase 43–52: migration integration test is missing.
- Phase 5–7: monitoring/AML runbooks and implementation note are missing.
- Phase 33–42: production deployment workflow lacks the release-calendar gate.
- Source migrations stop at V82 while the Phase II plan expects V84.

These findings do not invalidate the Phase II feature code, but they remain
Production NO-GO controls and must be closed during the later hardening cycle.
