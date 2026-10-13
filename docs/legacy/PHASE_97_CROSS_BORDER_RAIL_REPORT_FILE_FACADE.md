# Phase 97: Cross-Border Rail Report/File Facade

Status: closed
Date: 2026-08-13

## Delivered

Legacy report/file facades:

- `GET /crossborder/settlementfile?rail={rail}&statementDate={yyyy-MM-dd}`
- `GET /crossborder/crossborder_report?rail={rail}&statementDate={yyyy-MM-dd}`
- `GET /crossborder/report/transactions?rail={rail}&statementDate={yyyy-MM-dd}`
- `GET /crossborder/itmx/settlementfile?statementDate={yyyy-MM-dd}`
- `GET /crossborder/itmx/feesettlementfile?statementDate={yyyy-MM-dd}`
- `GET /crossborder/itmx/report?statementDate={yyyy-MM-dd}`
- `GET /crossborder/itmx/thaiscan_settlementfile?statementDate={yyyy-MM-dd}`
- `GET /crossborder/itmx/thaiscanlao_fee_settlementfile?statementDate={yyyy-MM-dd}`
- `GET /crossborder/napas/settlementfile?statementDate={yyyy-MM-dd}`
- `GET /crossborder/napas/settlementreport?statementDate={yyyy-MM-dd}`
- `GET /crossborder/napas/settlementbankreport?statementDate={yyyy-MM-dd}`
- `GET /crossborder/upi/settlementlakfile?statementDate={yyyy-MM-dd}`
- `GET /crossborder/upi/report?statementDate={yyyy-MM-dd}`
- `GET /crossborder/upi/summary?statementDate={yyyy-MM-dd}`
- `GET /crossborder/ecny/settlementfile?statementDate={yyyy-MM-dd}`
- `GET /crossborder/ecny/settlementreport?statementDate={yyyy-MM-dd}`
- `GET /crossborder/ecny/settlementbankreport?statementDate={yyyy-MM-dd}`
- `GET /lfx/settlementfile?statementDate={yyyy-MM-dd}`
- `GET /lfx/report?statementDate={yyyy-MM-dd}`

## Controls

- OPS/ADMIN web authorization.
- `PERM_SETTLEMENT_VIEW` method permission.
- No `/crossborder/upi/login` endpoint.
- No hardcoded rail URLs or credentials.
- No hardcoded bank pair SQL.
- Audit event: `CROSS_BORDER_LEGACY_RAIL_FILE_EXPORTED`.

## Data Source

Exports use existing production tables:

- `cross_border_rail_message`
- `cross_border_rail_reconciliation`

Legacy rail mapping:

- ITMX, ThaiScan, ThaiScan Lao use `PROMPTPAY` source data.
- NAPAS uses `NAPAS`.
- UPI uses `UPI`.
- e-CNY and LFX facades are guarded empty exports until active rail data exists.

## Formats

Settlement files use pipe-delimited rows:

- `HDR`
- `MSG`
- `REC`
- `TRL`
- `CHK`

Reports use CSV with message and reconciliation sections.

## Verification

Run:

```bash
./mvnw -Dtest=CrossBorderLegacyRailFacadeControllerTest,CrossBorderLegacyRailFacadeServiceTest test
```
