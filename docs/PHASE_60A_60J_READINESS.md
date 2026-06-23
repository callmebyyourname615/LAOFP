# Phase 60A–60J — Readiness Closure

Phase 60 converts the Go-Live critical-path implementation into repeatable certification gates. It does not claim runtime PASS merely because scripts exist.

| Phase | Gate | Repository implementation | Runtime dependency |
|---|---|---|---|
| 60A | Repository baseline | Git hygiene, migration inventory, required-file and deletion checks | None |
| 60B | Build/test closure | Full Maven and static gate runner, Surefire/Failsafe summary | Maven dependencies + Testcontainers |
| 60C | Migration certification | V1–V100 inventory/checksums, V97 SMOS and V100 reporting-repair contracts | PostgreSQL/Testcontainers; optional production-like upgrade DB |
| 60D | SMOS security E2E | MFA, refresh rotation, lockout, disable/revoke, tamper and audit tests | PostgreSQL/Testcontainers |
| 60E | Dashboard/promotion acceptance | Representative dashboard data tests and bounded JSON promotion DSL | PostgreSQL/Testcontainers |
| 60F | Secret/history closure | Six-secret inventory, attestation validator, hygiene and history scan | SecOps rotation, mirror purge, cache/clone invalidation |
| 60G | UAT infrastructure contract | Environment/Kubernetes validator and live TLS/TCP/health probe | Resolved UAT configuration and endpoints |
| 60H | Performance/capacity | 100/2K/10K/20K/8h runners, settlement 500K and evidence verifier | Production-like UAT capacity |
| 60I | Resilience evidence | Backup/PITR, six DR scenarios, alert inventory and synthetic route drill | UAT, backup keys, SRE/Ops sign-off |
| 60J | UAT entry gate | Hash manifest, image-digest binding, approval and compressed evidence bundle | 60A–60I PASS on one commit/image pair |

## Safe execution modes

```bash
# No Maven, load, secret rotation or failure injection
./scripts/phase60/run_phase60.sh --preflight

# Execute repository gates 60A–60E; keep operator/runtime gates in preflight
./scripts/phase60/run_phase60.sh --repo

# Execute all gates on UAT only
TARGET_ENVIRONMENT=uat \
PHASE60_EXECUTE_RUNTIME=true \
CONFIRM_UAT_DRILLS=yes \
APPLICATION_IMAGE_DIGEST=sha256:<64-hex> \
MIGRATION_IMAGE_DIGEST=sha256:<64-hex> \
./scripts/phase60/run_phase60.sh --full
```

The full run additionally requires paths to completed operator attestations:

- `SECRET_ROTATION_ATTESTATION`
- `PERFORMANCE_ATTESTATION`
- `RESILIENCE_ATTESTATION`
- `UAT_ENTRY_ATTESTATION`

Templates are under `docs/templates/`. They intentionally contain no secret values.

## Evidence rules

Every phase emits `scripts/phase60/evidence/<run-id>/<phase>/result.json`. A final 60J bundle is accepted only when:

1. 60A–60J are all `PASS`.
2. Results are bound to one full Git commit.
3. Runtime gates were executed with `TARGET_ENVIRONMENT=uat`.
4. Application and migration image references are SHA-256 digests.
5. Every artifact hash verifies.
6. QA, Security, SRE, Engineering and Product approve UAT entry.

`PREPARED` means code/tooling exists but runtime/operator evidence has not been produced. It is never equivalent to `PASS`.
