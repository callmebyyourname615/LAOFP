# Phase 60A-60J Readiness

## Scope

Phase 60 is the repository-to-UAT readiness gate. It packages static checks, Maven verification, migration inventory, SMOS security, dashboard acceptance, secret rotation readiness, UAT infrastructure contract, performance evidence, resilience evidence, and final evidence bundle assembly.

## Phase Map

| Phase | Gate | Evidence |
|---|---|---|
| 60A | Repository baseline | Repository baseline result |
| 60B | Build and test closure | Static contract and Maven result |
| 60C | Migration certification | Migration inventory result |
| 60D | SMOS security E2E | Security certification result |
| 60E | Dashboard and promotion acceptance | Dashboard data acceptance result |
| 60F | Secret rotation readiness | Secret rotation attestation |
| 60G | UAT infrastructure contract | UAT entry attestation |
| 60H | Performance and capacity | Performance attestation |
| 60I | Resilience evidence | Resilience attestation |
| 60J | Evidence bundle and UAT entry gate | Evidence manifest |

## Execution

Repository preflight:

```bash
./scripts/phase60/run_phase60.sh --preflight
```

Repository-only gates:

```bash
./scripts/phase60/run_phase60.sh --repo
```

Full UAT evidence run:

```bash
TARGET_ENVIRONMENT=uat PHASE60_EXECUTE_RUNTIME=true CONFIRM_UAT_DRILLS=true ./scripts/phase60/run_phase60.sh --full
```

## Production Readiness Rule

Phase 60 static readiness can pass without contacting UAT. Production readiness still requires signed runtime attestations for secret rotation, UAT entry, performance, resilience, and the final evidence manifest.
