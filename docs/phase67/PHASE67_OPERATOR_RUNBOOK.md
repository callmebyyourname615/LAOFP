# Phase 67 Operator Runbook

## Preflight

```bash
PHASE67_MODE=preflight scripts/phase67/run_phase67.sh
```

Preflight may be executed on a developer or CI runner. Results are written under `build/phase67-production-cutover/` and contain no production credentials.

## Import or execute

Export a single release identity before running any phase:

```bash
export RELEASE_REFERENCE=CHG-2026-0001
export RELEASE_RC_ID=switching-1.0.0
export RELEASE_GIT_COMMIT=<40-character-commit>
export RELEASE_APP_IMAGE_DIGEST=sha256:<64-hex>
export RELEASE_MIGRATION_IMAGE_DIGEST=sha256:<64-hex>
export PHASE55_ROOT=build/phase55-golive
export PHASE67_ROOT=build/phase67-production-cutover
```

For live execution also export:

```bash
export PHASE67_MODE=execute
export PRODUCTION_EXECUTION_CONFIRMATION=I_UNDERSTAND_THIS_OPERATES_ON_PRODUCTION
```

Run the phases individually after their Phase 55 prerequisite exists. Use `PHASE67_ENVIRONMENT=release` for 67A/67C, `production` for 67B/67D–67H, and `hypercare` for 67I/67J.

## Required external evidence

- 67A: `CHANGE_FREEZE_ATTESTATION`
- 67I: `PHASE67_HYPERCARE_CHECKPOINTS_FILE` containing Day 1, 3, 7, and 14 checkpoints
- 67J execute mode: `PHASE67_SIGNING_KEY` and `PHASE67_SIGNING_PUBLIC_KEY`

## Fail-closed behavior

Do not bypass a non-zero exit code. Inspect the phase result, decision, and log files. A gate may be rerun only after setting:

```bash
export PHASE67_RERUN_CONFIRMATION=I_UNDERSTAND_THIS_ARCHIVES_THE_PREVIOUS_ATTEMPT
```

The prior attempt is moved into `build/phase67-production-cutover/attempts/`.
