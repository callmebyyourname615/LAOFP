# Phase 61A–61J — UAT Certification Closure

Phase 61 converts Phase 60 repository readiness from `PREPARED` into evidence-backed `PASS` gates.

| Phase | Gate | Repository mode | Full UAT mode |
|---|---|---|---|
| 61A | Build/test green closure | Executes Maven | Executes Maven + repeat critical tests |
| 61B | Migration/data integrity | Executes targeted migration tests | Same revision against UAT-like DB |
| 61C | UAT deployment contract | Static contract | Live dependency/TLS/digest probes |
| 61D | SMOS hardening | Executes security tests | Security acceptance evidence |
| 61E | Dashboard/promotion | Executes acceptance tests | UAT data/RBAC acceptance |
| 61F | Secrets/supply chain | Static hygiene | Signed rotation, scan, SBOM, Cosign, provenance |
| 61G | Performance/capacity | Runner validation | 100/2K/10K/20K/8h evidence |
| 61H | Settlement/reconciliation | Verifier validation | 500K financial integrity proof |
| 61I | Backup/PITR/HA/DR/alerts | Contract validation | Destructive UAT drills and alert lifecycle |
| 61J | Evidence/RC gate | Tool validation | Immutable manifest and Phase 54 entry approval |

## Commands

```bash
scripts/phase61/run_phase61.sh --preflight
scripts/phase61/run_phase61.sh --repo
TARGET_ENVIRONMENT=uat PHASE61_EXECUTE_RUNTIME=true CONFIRM_UAT_DRILLS=yes \
  scripts/phase61/run_phase61.sh --full
```

`--full` must only run against UAT. The scripts intentionally require explicit confirmations and operator attestations.

## Phase 61I topology-specific command hooks

The repository cannot safely guess how managed PostgreSQL, Vault, or object storage are failed over. Full mode therefore requires explicit UAT-only command hooks. Each command must block until recovery verification is complete and return non-zero on failure.

```bash
export PHASE61_FULL_BACKUP_COMMAND='...'
export PHASE61_VERIFY_BACKUP_COMMAND='...'
export PHASE61_PITR_COMMAND='...'
export PHASE61_POSTGRES_FAILOVER_COMMAND='...'
export PHASE61_POSTGRES_FAILBACK_COMMAND='...'
export PHASE61_VAULT_FAILOVER_COMMAND='...'
export PHASE61_OBJECT_STORAGE_FAILOVER_COMMAND='...'
export ALERTMANAGER_URL='https://alertmanager.uat.example.invalid'
```

The resulting logs are hashed under the Phase 61I evidence directory. An operator attestation cannot substitute for missing runtime logs.

## Baseline migration consistency

Phase 61 validates the repository it is run against. The current delivery baseline contains 90 migration files through V101 and reserves V88–V96 plus V98–V99. If Phase II V91–V96 exist on another branch or ZIP, merge that authoritative source before running repository or full certification and update the expected migration inventory without renumbering already-applied migrations.
