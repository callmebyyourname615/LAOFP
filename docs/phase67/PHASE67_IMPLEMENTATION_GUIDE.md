# Phase 67A–67J — Production Cutover Control and Hypercare Evidence Gate

## Purpose

Phase 67 adds a fail-closed control layer over the existing Phase 55 Go-Live framework. It does not deploy workloads, migrate a database, alter ingress weights, or rotate secrets. It verifies Phase 55 evidence, records decisions, and creates a signed BAU acceptance bundle.

## Ownership boundary

Phase 67 owns only:

- `AGENT/PHASE_67A_67J_IMPLEMENTATION_CHECKLIST.md`
- `scripts/phase67/**`
- `config/phase67-production-cutover-policy.yaml`
- `schemas/phase67/**`
- `docs/phase67/**`
- `docs/templates/phase67/**`
- `.github/workflows/phase67-production-cutover.yml`

It must not modify application source, tests, database migrations, Phase 54/55/61/64 scripts, performance scenarios, backup tooling, DR tooling, or the master critical path document.

## Modes

- `preflight`: validates tooling and synthetic control behavior. It never consumes production evidence.
- `import`: validates evidence already produced by Phase 55.
- `execute`: validates live evidence and requires the explicit production confirmation. Phase 67J additionally requires an OpenSSL private/public signing key pair.

## Release identity

Every phase binds evidence to the same:

- release reference;
- release candidate ID;
- full Git commit SHA;
- application image digest;
- migration image digest.

Prerequisite checks fail when any identity field differs.

## Execution order

1. 67A — release identity and change-freeze gate
2. 67B — production infrastructure contract gate
3. 67C — immutable RC and provenance verification
4. 67D — financial cutover baseline verification
5. 67E — 5% canary health gate
6. 67F — 25%/50%/100% progressive traffic evidence gate
7. 67G — rollback decision engine
8. 67H — hash-chained command-center timeline
9. 67I — 14-day hypercare tracker
10. 67J — BAU operational acceptance bundle

## Safety

Phase 67 never promotes traffic automatically. A `HOLD` or `ROLLBACK_REQUIRED` decision returns non-zero and blocks subsequent phases. Financial mismatch, reconciliation mismatch, suspected data loss, duplicate business reference, or critical security incident produces `ROLLBACK_REQUIRED`.
