> **QA RESET — 2026-10-13:** Historical run evidence, test reports and validation outputs have been cleared for a new QA cycle. Current QA status is **NOT_RUN**. Previous PASS/NO_GO results or completion percentages in this implementation document are historical statements and must not be reused for release approval. The active QA tracker is `Report/CHECKLIST.csv`; fresh evidence must bind to the current release commit, image, schema and environment.

# Phase 99: Runtime Evidence Execution + Risk Re-score

Status: closed
Date: 2026-08-13

## Purpose

Phase 99 executes or consumes Phase 98 runtime evidence, then produces a machine-readable risk re-score. Static tests alone cannot raise the go-live gate. Runtime checks decide the final `GO_CANDIDATE` or `NO_GO`.

## Delivered

- `scripts/phase99/99-runtime-evidence-risk-rescore.sh`
- Output bundle: `runtime-evidence/phase99-risk-rescore-{timestamp}`
- Inputs:
  - `runtime-evidence/phase98-production-runtime-{timestamp}/score.json`
  - `runtime-evidence/phase98-production-runtime-{timestamp}/checks.jsonl`
- Outputs:
  - `risk-rescore.json`
  - `SUMMARY.md`
  - `PHASE99_CLOSURE.md`
  - `SHA256SUMS`

## Run Existing Phase 98 Evidence

```bash
PHASE98_EVIDENCE_DIR=runtime-evidence/phase98-production-runtime-YYYYMMDDTHHMMSSZ \
scripts/phase99/99-runtime-evidence-risk-rescore.sh
```

## Execute Phase 98 Then Re-score

```bash
RUN_PHASE98=true \
BASE_URL=https://175.11.0.200 \
COMPOSE_DIR=/opt/switching \
COMPOSE_SERVICE=app \
DB_SERVICE=db \
DB_NAME=switching \
DB_USER=switching \
STATEMENT_DATE=2026-08-13 \
ACCESS_TOKEN='...' \
scripts/phase99/99-runtime-evidence-risk-rescore.sh
```

## Decision Rules

- `GO_CANDIDATE`: no `FAIL`, `BLOCKED`, `WARN`, or `NOT_RUN`.
- `NO_GO`: any `FAIL`, `BLOCKED`, `WARN`, or `NOT_RUN`.
- `productionReadyPercent`:
  - `90%` when runtime bundle is full clean pass.
  - `84%` when no hard fail/block exists, but warning/not-run remains.
  - `78%` when fail/block exists.

## QA execution state after reset

QA status: `NOT_RUN` (reset 2026-10-13).

Previous local execution bundles were deleted at the user's request. No current runtime verdict or readiness percentage is available. Run Phase 98 on the selected candidate environment with Docker/DB capture and valid secure credentials, then run Phase 99 against that fresh bundle.
