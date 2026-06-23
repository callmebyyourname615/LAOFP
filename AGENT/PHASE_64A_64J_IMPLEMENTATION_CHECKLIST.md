# Phase 64A–64J Implementation Checklist

Scope ownership is intentionally limited to Phase 64 files. Do not modify application code, tests, Flyway migrations, Phase 61 scripts, or shared production configuration.

## 64A — UAT environment readiness
- [x] Add guarded UAT-only execution contract.
- [x] Validate Phase 61 UAT infrastructure contract and HTTPS management endpoint.
- [x] Persist machine-readable readiness evidence.

## 64B — Phase 61 evidence acquisition
- [x] Support verified import of an existing Phase 61 manifest.
- [x] Support explicit execution of Phase 61 full mode.
- [x] Verify release commit and application/migration image digests.
- [x] Copy evidence into the Phase 64 chain of custody.

## 64C — Runtime evidence acquisition
- [x] Support verified import of runtime evidence.
- [x] Support explicit execution of the full runtime evidence plan.
- [x] Verify runtime evidence hashes and release identity.
- [x] Write chain-of-custody metadata.

## 64D — Build, test, migration and sanctions evidence
- [x] Require static gates, Maven verify, migration runtime and sanctions controls to pass.
- [x] Produce a test evidence summary without changing test code.

## 64E — Performance and capacity evidence
- [x] Validate smoke, sustained 2K, sustained 10K, burst 20K and soak evidence.
- [x] Enforce configured error-rate, latency, throughput and dropped-iteration thresholds.
- [x] Produce a performance summary bound to the release identity.

## 64F — Backup, restore and PITR evidence
- [x] Validate backup checksum, row counts, restore, PITR, RPO and RTO attestation.
- [x] Require runtime backup/restore control to pass.

## 64G — DR and failure-recovery evidence
- [x] Validate every required DR scenario.
- [x] Require zero committed transaction loss and idempotent outbox replay.
- [x] Require runtime DR control to pass.

## 64H — Alert-firing certification
- [x] Enumerate Prometheus alerts from repository rules.
- [x] Verify alert/runbook contracts.
- [x] Require fired, routed and resolved evidence for every current alert.
- [x] Require runtime alert-delivery control to pass.

## 64I — Phase 54 entry gate
- [x] Require 64A–64H PASS with one release identity.
- [x] Verify source evidence manifests remain hash-valid.
- [x] Emit machine-readable APPROVE/BLOCK decision.

## 64J — Signed UAT handoff bundle
- [x] Validate human approval attestation.
- [x] Build deterministic evidence manifest and ZIP bundle.
- [x] Generate SHA-256 checksums.
- [x] Sign and verify the manifest with OpenSSL in full mode.

## Verification and delivery
- [x] Add Phase 64 configuration, schemas, templates and operator guide.
- [x] Add static verifier and focused unit tests.
- [x] Add additive CI workflow and execute-and-verify entry point.
- [x] Run shell syntax checks.
- [x] Run JSON/YAML validation.
- [x] Run Phase 64 unit/static tests.
- [ ] Run Maven compile. — BLOCKED: Maven Wrapper download unavailable in offline sandbox.
- [x] Attempt Maven verify and record any pre-existing blockers accurately.
- [x] Run Phase 64 preflight orchestrator.
- [x] Package only changed files with validation report and checksum.

## External runtime completion
- [ ] Execute Phase 64 full mode against approved UAT infrastructure.
- [ ] Close the 58 existing alert/runbook reference gaps before full gate approval.
- [ ] Run fresh Maven compile and verify in connected CI/development environment.
