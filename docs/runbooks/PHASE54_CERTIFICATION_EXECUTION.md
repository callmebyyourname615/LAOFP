# Phase 54 Certification Execution Runbook

## 1. Prepare the certification runner

Use a hardened Linux runner with Java 21, Maven wrapper access, Docker, kubectl, PostgreSQL client tools, Python 3, PyYAML, k6 Docker access, gitleaks, Trivy, Syft, Cosign, and network access to the UAT cluster and monitoring endpoints.

Never run the destructive phases against production. Confirm the current Kubernetes context before each cluster phase.

## 2. Establish immutable release identity

```bash
export CERTIFICATION_ENVIRONMENT=uat
export CERTIFICATION_ROOT="$PWD/build/phase54-certification"
export RELEASE_REFERENCE=CHG-2026-000123
export RELEASE_GIT_COMMIT="$(git rev-parse HEAD)"
export RELEASE_IMAGE_REPOSITORY=ghcr.io/example/switching/switching-api
export RELEASE_IMAGE_DIGEST=sha256:<digest>
```

Verify the checked-out commit and image digest before continuing.

## 3. Run phases independently

```bash
scripts/certification/run_phase54_certification.sh 54A
scripts/certification/run_phase54_certification.sh 54B
scripts/certification/run_phase54_certification.sh 54C
scripts/certification/run_phase54_certification.sh 54D
scripts/certification/run_phase54_certification.sh 54E
scripts/certification/run_phase54_certification.sh 54F
scripts/certification/run_phase54_certification.sh 54G
scripts/certification/run_phase54_certification.sh 54H
scripts/certification/run_phase54_certification.sh 54I
scripts/certification/run_phase54_certification.sh 54J
```

The scripts require explicit confirmations for traffic generation, UAT deployment, destructive DR, backup restore, and canary rollback.

## 4. Required confirmations

```bash
export UAT_REHEARSAL_CONFIRMATION=I_UNDERSTAND_THIS_DEPLOYS_AND_ROLLS_BACK_UAT
export PERFORMANCE_CERTIFICATION_CONFIRMATION=I_UNDERSTAND_THIS_GENERATES_SUSTAINED_LOAD
export SETTLEMENT_CERTIFICATION_CONFIRMATION=I_UNDERSTAND_THIS_SEEDS_500000_TRANSACTIONS
export BACKUP_CERTIFICATION_CONFIRMATION=I_UNDERSTAND_THIS_RUNS_BACKUP_AND_ISOLATED_RESTORE
export DR_CERTIFICATION_CONFIRMATION=I_UNDERSTAND_THIS_IS_DESTRUCTIVE
export GOLIVE_REHEARSAL_CONFIRMATION=I_UNDERSTAND_THIS_RUNS_CANARY_PROMOTION_AND_ROLLBACK
```

For Phase 54F, provide the observed WAL marker RPO:

```bash
export PITR_RPO_SECONDS_OBSERVED=<measured-seconds>
```

## 5. Full sequence

The full sequence includes an 8-hour soak and disruptive drills:

```bash
scripts/certification/run_phase54_certification.sh full
```

Use a persistent `CERTIFICATION_ROOT`; do not delete it between phases.

## 6. Verify the package

```bash
python3 scripts/certification/verify_certification_manifest.py \
  build/phase54-certification/manifest.json \
  --require-ready \
  --expected-commit "$RELEASE_GIT_COMMIT" \
  --expected-digest "$RELEASE_IMAGE_DIGEST" \
  --expected-reference "$RELEASE_REFERENCE"
```

Do not approve Go-Live if the command exits non-zero.

## 7. Failure handling

- Keep failed evidence; do not edit logs or result JSON.
- Correct the environment or code issue under a new commit/digest when applicable.
- Archive the previous attempt with the rerun confirmation.
- Never mark a failed or missing phase as PASS manually.
- Database migrations are forward-fixed; the application rollback does not reverse V83.
