# Phase 72 Operator Runbook

## Preflight

```bash
./scripts/phase72/run_phase72.sh --preflight
```

Preflight validates contracts and generates a `PREPARED` or `BLOCKED` bundle. It does not certify UAT.

## Full execution

Run only after Phase 71 is merged and UAT is provisioned. Export endpoints and operator command hooks without printing credentials. Then explicitly enable the relevant operations:

```bash
export PHASE72_CONFIRM_FULL=YES
export PHASE72_CONFIRM_UAT=YES
export PHASE72_CONFIRM_LOAD=YES
export PHASE72_CONFIRM_BACKUP_RESTORE=YES
export PHASE72_CONFIRM_DR=YES
export PHASE72_CONFIRM_SECRET_ROTATION=YES
export PHASE72_CONFIRM_RUNTIME_SECURITY=YES
export PHASE72_FINAL_ATTESTATION=/secure/path/final-go-attestation.json
./scripts/phase72/run_phase72.sh --full
```

Backup, restore, DR, Kafka, MinIO, Vault, SMOS and alert checks are supplied as operator commands through environment variables. Do not place credentials or command output containing credentials in the repository.

The final manifest is written below `evidence/phase72/<run-id>/`.
