# Phase 95: Legacy Settlement File Compatibility

Status: closed
Date: 2026-08-12

## Delivered

Phase 95 adds guarded legacy compatibility endpoints for the highest-value LMPSCore settlement/report/file routes:

- `GET /settlementfile?cycleRef={cycleRef}`
- `GET /report/settlement?cycleRef={cycleRef}`
- `GET /report/settlement_lapnet?cycleRef={cycleRef}`
- `GET /report/settlement_v2?cycleRef={cycleRef}`
- `GET /finlink/report/settlement?cycleRef={cycleRef}`
- `GET /finlink/report/settlement_v2?cycleRef={cycleRef}`
- `GET /finlink/report/lapnet_settlement?cycleRef={cycleRef}`
- `GET /finlink/getrtgsjournal?instructionRef={instructionRef}`
- `GET /finlink/getrtgsjournal?rtgsMsgId={rtgsMsgId}`

## Controls

- Read-only compatibility facade.
- Requires `PERM_SETTLEMENT_VIEW` and OPS/ADMIN web authorization.
- Uses current settlement cycle, position, instruction, transfer, and report artifact data.
- No hardcoded bank pairs, partner URLs, credentials, or SQL bank selectors.
- Generates audit log events:
  - `LEGACY_SETTLEMENT_FILE_EXPORTED`
  - `LEGACY_RTGS_JOURNAL_EXPORTED`
- Settlement exports require current cycle status `SETTLED` through `SettlementOpsReportService`.

## Formats

`/settlementfile` returns pipe-delimited legacy-compatible file:

- `HDR` row with cycle metadata.
- `POS` rows with bank net positions.
- `INS` rows with RTGS settlement instructions.
- `TRL` row with totals.
- `CHK` row with SHA-256 over file body.

Report endpoints return CSV. `settlement_v2` variants include instruction rows.

`/finlink/getrtgsjournal` returns RTGS journal JSON by `instructionRef` or `rtgsMsgId`.

## Verification

Run:

```bash
./mvnw -Dtest=LegacySettlementCompatibilityServiceTest,LegacySettlementCompatibilityControllerTest test
```
