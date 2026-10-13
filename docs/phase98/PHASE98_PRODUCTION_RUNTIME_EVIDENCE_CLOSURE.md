> **QA RESET — 2026-10-13:** Historical run evidence, test reports and validation outputs have been cleared for a new QA cycle. Current QA status is **NOT_RUN**. Previous PASS/NO_GO results or completion percentages in this implementation document are historical statements and must not be reused for release approval. The active QA tracker is `Report/CHECKLIST.csv`; fresh evidence must bind to the current release commit, image, schema and environment.

# Phase 98: Production Runtime Evidence Closure

Status: closed
Date: 2026-08-13

## Purpose

Phase 98 closes the runtime-evidence blocker after the Phase 97 legacy cross-border facade. It does not claim production readiness from static tests alone. It adds a repeatable Docker-server evidence collector that operators can run against UAT or production-candidate runtime.

## Delivered

- `scripts/phase98/98-production-runtime-evidence-closure.sh`
- Runtime bundle layout under `runtime-evidence/phase98-production-runtime-{timestamp}`
- Redacted login handling; access and refresh tokens are not written to disk.
- Docker state capture for compose/container/image/log evidence.
- Phase 97 endpoint runtime probes:
  - `GET /crossborder/itmx/settlementfile`
  - `GET /crossborder/napas/settlementreport`
  - `GET /crossborder/upi/summary`
  - `GET /lfx/report`
- Optional DB evidence:
  - Flyway version state.
  - `CROSS_BORDER_LEGACY_RAIL_FILE_EXPORTED` audit events.
- Machine-readable `manifest.json`, `score.json`, `checks.jsonl`, and `SHA256SUMS`.

## Server Command

Run this on the Docker server from the deployed repository directory:

```bash
BASE_URL=https://175.11.0.200 \
COMPOSE_DIR=/opt/switching \
COMPOSE_SERVICE=app \
DB_SERVICE=db \
DB_NAME=switching \
DB_USER=switching \
STATEMENT_DATE=2026-08-13 \
SMOS_USERNAME=admin \
SMOS_PASSWORD='...' \
scripts/phase98/98-production-runtime-evidence-closure.sh
```

If an access token is already available, avoid password login:

```bash
BASE_URL=https://175.11.0.200 \
ACCESS_TOKEN='...' \
STATEMENT_DATE=2026-08-13 \
scripts/phase98/98-production-runtime-evidence-closure.sh
```

## Pass Criteria

- `/actuator/health` is `UP`.
- `/api/operations/health` is `UP` or explicitly reviewed if `WARN`.
- Phase 97 rail facade endpoints return HTTP 200.
- ITMX output maps to `PROMPTPAY` source data and has a `CHK` row.
- NAPAS, UPI, and LFX report facades return the expected CSV summary rows.
- Flyway evidence shows deployed schema state.
- Audit evidence shows `CROSS_BORDER_LEGACY_RAIL_FILE_EXPORTED` after protected endpoint calls.
- Bundle has `SHA256SUMS`.

## Closure Result

Repository closure is complete. Runtime execution must be performed on the Docker server to generate fresh environment-bound evidence. The script intentionally exits non-zero on failed or blocked critical checks.
