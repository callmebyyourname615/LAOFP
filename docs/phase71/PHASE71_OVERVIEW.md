# Phase 71 — UAT Certification Closure

Phase 71 converts the existing production-readiness frameworks into one evidence-bound UAT execution chain.

## Rules

- `--preflight` validates repository contracts and never claims runtime success.
- `--repo` runs repository-safe checks only.
- `--full` requires `TARGET_ENVIRONMENT=uat` and explicit execution flags.
- All PASS results must bind to one Git commit, application image digest, and migration image digest.
- Operator attestations are accepted only with non-empty SHA-256 evidence references.
- Missing authoritative migrations or phase runners produce `BLOCKED`, never synthetic PASS.

## Execution

```bash
scripts/phase71/run_phase71.sh --preflight
scripts/phase71/run_phase71.sh --repo

TARGET_ENVIRONMENT=uat \
PHASE71_EXECUTE_UAT=true \
PHASE71_EXECUTE_OPERATOR_ACTIONS=true \
PHASE71_EXECUTE_LOAD=true \
PHASE71_EXECUTE_DR=true \
APPLICATION_IMAGE_DIGEST=sha256:... \
MIGRATION_IMAGE_DIGEST=sha256:... \
scripts/phase71/run_phase71.sh --full
```
