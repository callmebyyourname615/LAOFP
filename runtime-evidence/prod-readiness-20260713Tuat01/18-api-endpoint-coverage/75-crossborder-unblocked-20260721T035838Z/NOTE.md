# Cross-Border module unblocked (PHASE_II_CROSS_BORDER_ENABLED=true redeployed)

## Endpoints exercised

| Endpoint | Result |
|---|---|
| POST `/v1/crossborder/inbound/{rail}` | ROUTE + VALIDATION PASS. Blocked at business config check ("Inbound partner authentication is not configured") because `PROMPTPAY_INBOUND_API_KEY` is empty in UAT — per-rail secret not provisioned. |
| POST `/v1/operator/crossborder/reconciliation/{rail}/{statementDate}` | **PASS 200** — full happy path with empty statement, returned correct 0-count reconciliation result. |

## Follow-ups (findings, not part of endpoint-coverage scope)

- Same MissingRequestHeader → 500 bug as the MissingRequestParam one from
  `71-report-delivery-unblocked`. `CrossBorderInboundController` has 4 non-optional
  `@RequestHeader` params (X-Partner-Key, X-External-Reference, X-Message-Type, X-Signature); any
  missing one yields 500 SYS-001 instead of a specific 400 that names the header. `GlobalExceptionHandler`
  needs a handler for `MissingRequestHeaderException` + `MissingServletRequestParameterException`.
- No per-rail inbound API keys are provisioned in UAT `.env`. The endpoint is reachable now but
  no partner can actually push through until `PROMPTPAY_INBOUND_API_KEY`, `BAKONG_INBOUND_API_KEY`,
  `NAPAS_INBOUND_API_KEY`, `UPI_INBOUND_API_KEY` (whichever rails apply) are set to ≥ 32-char
  secrets shared with the partner. Same category of "feature flag on, secrets missing" as
  Report Delivery's `REPORT_LINK_SIGNING_SECRET`.
