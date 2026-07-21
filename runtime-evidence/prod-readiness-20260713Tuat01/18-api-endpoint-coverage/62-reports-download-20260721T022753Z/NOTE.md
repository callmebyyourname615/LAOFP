# GET /v1/reports/download/{id} — BLOCKED (same root cause as RTP / report-delivery-schedules)

`ReportDownloadController` (src/main/java/com/example/switching/reportdelivery/ReportDownloadController.java)
is gated by `@ConditionalOnProperty(prefix = "switching.phase-ii.report-delivery", name = "enabled", havingValue = "true")`
— identical flag to `ReportScheduleController`.

`PHASE_II_REPORT_DELIVERY_ENABLED` is not declared in docker-compose.yml's `app` service environment block,
so this route can never register on the current UAT deployment regardless of .env contents.

Result: 404 REQ-005 "API route not found" — same failure mode as evidence 60/61.

Not counted as a standalone testable gap; folded into the report-delivery deploy-gap group
(6 rtp + 3 report-delivery-schedules + 1 report-download + 3 crossborder = 13 endpoints blocked).

Fix: add `PHASE_II_REPORT_DELIVERY_ENABLED`, `REPORT_DELIVERY_POLL_MS`, `REPORT_DOWNLOAD_BASE_URL`,
`REPORT_LINK_SIGNING_SECRET` to docker-compose.yml `app.environment`, then redeploy and re-run.
