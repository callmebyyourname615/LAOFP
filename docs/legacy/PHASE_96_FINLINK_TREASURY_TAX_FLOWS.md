# Phase 96: Finlink Treasury and Tax Flows

Status: closed
Date: 2026-08-12

## Delivered

Phase 96 adds controlled Finlink legacy business coverage for:

- `POST /finlink/transit_fund_move`
- `POST /finlink/inform_LIFMIS`
- `GET /finlink/settlementfileeasytax?businessDate={yyyy-MM-dd}`
- `GET /finlink/adjustment_settlementfileeasytax?businessDate={yyyy-MM-dd}`
- `GET /finlink/roadtax/settlementfile?businessDate={yyyy-MM-dd}`

## Controls

- OPS/ADMIN web authorization.
- Method permission gate:
  - fund movement and LIFMIS inform require `PERM_SETTLEMENT_APPROVE`
  - file export requires `PERM_SETTLEMENT_VIEW`
- Idempotency via `idempotency_key`.
- Balanced double-entry posting for `/finlink/transit_fund_move`.
- Append-only operational evidence in `finlink_treasury_event`.
- Audit events:
  - `FINLINK_TREASURY_EVENT_RECORDED`
  - `FINLINK_LIFMIS_INFORMED`
  - `FINLINK_SETTLEMENT_FILE_EXPORTED`
- No hardcoded bank pair SQL.
- No hardcoded external LIFMIS endpoint or credential.

## Data Model

Migration:

- `V118__finlink_treasury_tax_flows.sql`

Tables/accounts:

- `finlink_treasury_event`
- `FINLINK_TRANSIT_CLEARING`
- `FINLINK_SETTLEMENT_PAYABLE`
- `FINLINK_LIFMIS_RECEIVABLE`
- `FINLINK_TAX_PAYABLE`
- `FINLINK_ROADTAX_PAYABLE`

## Verification

Run:

```bash
./mvnw -Dtest=FinlinkTreasuryControllerTest,FinlinkTreasuryServiceFormattingTest test
```
