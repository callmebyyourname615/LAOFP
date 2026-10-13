# Phase II Cross-Border Adapters Runbook

## Checks

- Confirm `PHASE_II_CROSS_BORDER_ENABLED=true` only for certified rails.
- Partner endpoints must use HTTPS, with HTTP allowed only for loopback tests.
- Inbound partner messages must include `X-Partner-Key` and valid signature.

## Smoke

- Submit signed inbound message.
- Confirm payload size stays below maximum.
- Confirm compliance screening clears before dispatch.
- Confirm rail journal records request hash and external reference.

## Failure Handling

- Replayed rail external reference with different content must fail closed.
- Compliance blocked messages emit `cross_border.compliance_blocked`.
- Reconciliation classifies missing internal and missing external records.
