# Request-to-Pay API — Phase II-01–04

Feature flag: `PHASE_II_RTP_ENABLED=true`.

## Create request

`POST /v1/rtp/requests`

```json
{
  "requestCorrelationId": "RTP-CLIENT-000001",
  "payeeParticipantId": "BANK_A",
  "payerParticipantId": "BANK_B",
  "payeeAccount": "010100000001",
  "payerAccount": "020200000001",
  "requestedAmount": 150000.00,
  "currency": "LAK",
  "description": "Invoice INV-001"
}
```

A new request returns `201 Created`. Replaying the same correlation ID with the
same canonical payload returns `200 OK` and the original RTP. Reusing the ID with
a different payload returns `409 RTP-002`.

## Read request

`GET /v1/rtp/requests/{id}`

BANK callers may read requests where they are payer or payee. OPS and ADMIN may
read all requests.

## Cancel request

`POST /v1/rtp/requests/{id}/cancel`

```json
{"reason":"Invoice withdrawn"}
```

Cancellation is idempotent after the first successful cancellation. Other
invalid state transitions return `409 RTP-003`.

## Current lifecycle

This delivery exposes `PENDING_AUTH` and `CANCELLED` through the API. The state
machine already defines the complete Phase II lifecycle for later authorisation,
partial payment, installment, settlement, decline, and expiry work.
