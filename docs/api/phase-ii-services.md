# Phase II API Contract

All paths use the configured API v1 prefix. New modules are disabled by default.

## Request-to-Pay

- `POST /v1/rtp/requests`
- `GET /v1/rtp/requests/{id}`
- `POST /v1/rtp/requests/{id}/authorise`
- `POST /v1/rtp/requests/{id}/decline`
- `POST /v1/rtp/requests/{id}/settlements`
- `POST /v1/rtp/requests/{id}/cancel`

Authorisation modes are `FULL`, `PARTIAL`, and `INSTALLMENT`. The payer must
provide a transfer inquiry reference. Installment numbers start at one, are
contiguous, and their amounts must equal the authorised amount.

## Promotion operator API

- `POST /v1/promotions`
- `POST /v1/promotions/{id}/activate`
- `PATCH /v1/promotions/{id}/suspend`
- `PATCH /v1/promotions/{id}/extend`
- `GET /v1/promotions/{id}/report`

Promotion activation uses four-eyes control. The creator cannot activate the
same promotion. Eligibility rules use a bounded JSON DSL and never execute
arbitrary expressions.

## Push-payment policy API

- `POST /v1/operator/push-payment-policies`
- `POST /v1/operator/push-payment-policies/{id}/activate`

Policies are versioned by channel. Activation retires the previous active
version and requires an approver different from the requester.

## Cross-border inbound and reconciliation

- `POST /v1/crossborder/inbound/{rail}`
- `POST /v1/operator/crossborder/reconciliation/{rail}/{statementDate}`

Inbound partner requests require all of:

- mTLS at the ingress/JVM layer;
- `X-Partner-Key`;
- `X-External-Reference`;
- `X-Message-Type`;
- `X-Signature` containing a 64-character HMAC-SHA256 digest.

Supported Phase II rail names are `PROMPTPAY`, `BAKONG`, `NAPAS`, and `UPI`.
UPI outward submission remains disabled pending accreditation.

## Report delivery operator API

- `POST /v1/operator/report-delivery-schedules`
- `PATCH /v1/operator/report-delivery-schedules/{id}/suspend`
- `GET /v1/operator/report-delivery-schedules`
- `GET /v1/reports/download/{artifactId}?expires=...&token=...`

Destination secrets are stored as `env:VARIABLE_NAME` references. They are not
persisted as plaintext. Signed download links are valid for no more than 24 hours.
