# Phase 94: Legacy Endpoint Coverage Mapping

Status: closed
Date: 2026-08-12

## Scope

Phase 94 maps the endpoint surface found in `/Users/macbookpro/Downloads/LMPSCore.jar` against this Switching project. The goal is not to port LMPSCore directly. The goal is to identify which legacy business contracts are already covered, which need compatibility work, and which should be rejected for production.

Source artifact checked:

- Path: `/Users/macbookpro/Downloads/LMPSCore.jar`
- SHA-256: `36a3d979c877e86aaf5ba4113345971ab4d7d21675e82daef47791328cc9faf9`
- Main class: `com.lapnet.LMPSCore`
- Legacy route strings mapped: 84

Current Switching references:

- `API_ENDPOINTS.txt`
- `API_ENDPOINTS_DARK.html`
- `runtime-evidence/api-endpoint-matrix-20260803T025033Z/coverage-summary.json`

## Coverage Result

| Status | Count | Meaning |
| --- | ---: | --- |
| covered | 8 | Switching has a matching or stronger canonical capability. |
| partial | 38 | Switching has a base capability, but exact legacy path, payload, report, file layout, or workflow is not proven. |
| missing | 34 | No equivalent business contract is currently mapped. |
| deprecated | 4 | Endpoint should not be implemented as-is for production. |
| total | 84 | Legacy LMPSCore route strings in the mapping. |

Detailed mapping: `docs/legacy/lmpscore-endpoint-coverage.csv`

## Do Not Implement As-Is

These routes are intentionally marked `deprecated`:

- `/admin/shutdown`
- `/admin/loadmembers`
- `/test`
- `/crossborder/upi/login`

Reason: they expose admin, diagnostic, or rail-auth behavior as direct routes. Switching should keep those controls behind internal operations, health/readiness checks, controlled jobs, or internal adapter credentials.

## Highest-Value Gaps

The largest legacy coverage gaps are not simple transfer/inquiry endpoints. Switching already has strong coverage there. The gaps are business-specific settlement/reporting/file workflows:

1. Finlink settlement and treasury: EasyTax, LIFMIS, transit fund move, RTGS journal compatibility.
2. Cross-border file/report formats: ITMX, NAPAS, UPI, e-CNY, LFX.
3. Government/biller verticals: road tax, land tax, electricity, FinPass, Fee002.
4. Legacy report/download layouts: LAPNet settlement, settlement v2, transaction report columns and filters.

## Recommended Next Phases

### Phase 95: Legacy Settlement File Compatibility

Implement compatibility formats for:

- `/settlementfile`
- `/report/settlement`
- `/report/settlement_lapnet`
- `/report/settlement_v2`
- `/finlink/report/settlement`
- `/finlink/report/settlement_v2`
- `/finlink/report/lapnet_settlement`
- `/finlink/getrtgsjournal`

Acceptance gate:

- Contract fixtures for file/report layout.
- Golden-file tests for generated output.
- Audit log for file generation and download.
- Runtime evidence under `runtime-evidence/`.

### Phase 96: Finlink Treasury and Tax Flows

Implement only the active Finlink business flows first:

- `/finlink/inform_LIFMIS`
- `/finlink/transit_fund_move`
- `/finlink/settlementfileeasytax`
- `/finlink/adjustment_settlementfileeasytax`
- `/finlink/roadtax/settlementfile`

Acceptance gate:

- Idempotency on every fund movement.
- Maker/checker or controlled approval where money movement is triggered.
- Reconciliation state transitions and rollback runbook.

### Phase 97: Cross-Border Rail Report/File Facade

Add compatibility facades for active rails:

- ITMX settlement and fee files.
- NAPAS settlement and bank reports.
- UPI LAK settlement and summary.
- e-CNY only if business confirms active use.
- LFX only if business confirms active use.

Acceptance gate:

- Rail-specific test fixtures.
- Signature/auth verification per rail.
- Cutoff/date handling tests.
- No hardcoded partner URLs or credentials.

### Phase 98: Government Biller Legacy Verticals

Implement only after product ownership confirms active consumers:

- Road tax inquiry/payment/report/check transaction/e-sticker.
- Land tax inquiry/payment/report/check transaction.
- Electricity inquiry/payment/basic data.
- FinPass inquiry/payment/cancel/QR inquiry/D53.
- Fee002.

Acceptance gate:

- Provider sandbox or captured contract evidence.
- Reversal/cancel behavior defined.
- Biller settlement reconciliation.
- Negative test matrix for expired bills, duplicate payments, provider timeout, and late confirmation.

## Phase 94 Closure Criteria

- Legacy route list is preserved in a machine-readable mapping.
- Each route has a status, domain, priority, current Switching coverage note, and recommended action.
- Dangerous/admin/test endpoints are explicitly blocked from direct implementation.
- Follow-up implementation phases are ordered by production value and risk.
- Verification script passes.

## Verification

Run:

```bash
python3 scripts/legacy/verify_phase94_legacy_mapping.py
```

Expected result:

```text
phase94 legacy mapping verified: 84 endpoints
covered=8 partial=38 missing=34 deprecated=4
```
