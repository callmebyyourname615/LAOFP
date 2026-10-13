# Phase II Services API Summary

## RTP

- `POST /v1/rtp/requests`
- `GET /v1/rtp/requests/{id}`
- `POST /v1/rtp/requests/{id}/cancel`
- `POST /v1/rtp/requests/{id}/authorise`
- `POST /v1/rtp/requests/{id}/decline`
- `POST /v1/rtp/requests/{id}/settlements`

## Promotions

- `/v1/promotions/**` is an operator-managed API surface.
- Budget reservations must be consumed, released, expired, or refunded through the reservation lifecycle.

## Push Payment Orchestration

- Transfer, QR, and bill payment submission services can route through the orchestrator when enabled.
- Idempotency keys are bound to request payload hashes.

## Cross-Border Rails

- `POST /v1/crossborder/inbound/{rail}` accepts signed partner inbound messages.
- Operator reconciliation uses `/v1/operator/crossborder/reconciliation/{rail}/{statementDate}`.

## Report Delivery

- `/v1/operator/report-delivery-schedules/**` is operator-managed.
- Signed report download links are time-limited.
