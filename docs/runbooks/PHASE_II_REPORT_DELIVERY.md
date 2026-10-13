# Phase II Report Delivery Runbook

## Checks

- Confirm `PHASE_II_REPORT_DELIVERY_ENABLED=true` only for approved recipients.
- Report destinations must resolve secrets from `env:NAME` references.
- SFTP uses strict host key checking.
- S3 and report download base URLs must use HTTPS outside loopback tests.

## Smoke

- Create report schedule.
- Generate report artifact idempotently.
- Deliver via configured destination.
- Confirm `report.delivered` event.
- Confirm signed download link expires within the configured TTL.

## Failure Handling

- Stale delivery claims are recovered by the report delivery worker.
- Failed delivery emits `report.delivery_failed`.
- Re-run only after destination credentials and artifact idempotency are verified.
