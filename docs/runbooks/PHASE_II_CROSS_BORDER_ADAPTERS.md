# Phase II Cross-Border Adapter Runbook

## Preflight

- Verify partner endpoint is HTTPS.
- Verify JVM key store and trust store for mTLS.
- Verify inbound API key, HMAC secret and partner identifier.
- Verify OAuth client credentials when required.
- Verify sanctions service and FX quote freshness.

## Replay handling

The unique identity is `(rail, direction, external_ref)`. A replay is accepted
only when the request hash, internal reference and message type are identical.
A different payload using an existing external reference is rejected.

## Reconciliation

Upload the partner statement to the operator reconciliation endpoint. Investigate:

- `MISSING_INTERNAL`
- `MISSING_EXTERNAL`
- `AMOUNT_MISMATCH`
- `CURRENCY_MISMATCH`

No discrepancy may be silently converted to `MATCHED`.
