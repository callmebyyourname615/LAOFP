# Report Delivery module unblocked

After deploying `PHASE_II_REPORT_DELIVERY_ENABLED=true` on UAT, all 4 previously-blocked endpoints
now register and respond with real business layers instead of `REQ-005 route not found`:

| Endpoint | Result | Verdict |
|---|---|---|
| `GET  /v1/operator/report-delivery-schedules` | 200 `[]` | PASS |
| `POST /v1/operator/report-delivery-schedules` | 400 "Missing cronExpression" | PASS (validation) |
| `PATCH .../{id}/suspend`                       | 400 "Active report schedule not found" | PASS (business 404) |
| `GET  /v1/reports/download/{id}` (with signed link) | 400 "Report link signing secret is not configured" | ROUTE PASS + config gap |

## Findings (not part of endpoint-coverage scope, worth flagging)

### F1 — REPORT_LINK_SIGNING_SECRET is unset in the UAT environment
docker-compose.yml defaults to empty and no override was added to `.env`. Any generated download
link will 400 with "Report link signing secret is not configured" — the download feature is
effectively non-functional until the secret is provisioned (must be ≥ 32 chars per
`ReportDownloadController.java:47`). Blocks any real e2e report delivery.

### F2 — Missing @RequestParam returns 500 SYS-001 instead of 400
`GET /v1/reports/download/{id}` requires two query parameters (`expires` long, `token` String)
per `ReportDownloadController.java:38-40`. Calling it without them returns 500 UNKNOWN instead of
a proper 400 with the missing parameter name. This is a `GlobalExceptionHandler` gap —
`MissingServletRequestParameterException` is not handled, so Spring's default returns generic
UNKNOWN. Affects API quality for every endpoint with required query params, not just this one.
