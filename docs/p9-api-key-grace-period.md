# P9 API Key Grace Period

## Status

P9 keeps `X-API-Key` as a temporary legacy credential while PSPs migrate to OAuth 2.0 Bearer tokens.

## Current Grace Behavior

- `Authorization: Bearer <token>` is preferred for bank-facing APIs.
- `X-API-Key` remains accepted during the grace period so existing PSP integrations do not break immediately.
- Signed mutating requests must include `X-Request-Signature` and `X-Timestamp` when request signing is enabled.
- mTLS must present a registered `X-Client-Cert` fingerprint when mTLS is enabled.
- OAuth token endpoints remain unsigned because they authenticate with `client_id` and `client_secret`.

## Cutover Behavior

At the end of the grace period:

- Bank-facing PSP traffic must use `Authorization: Bearer <token>`.
- Legacy `X-API-Key`-only PSP traffic will be rejected as `LFP-2001 INVALID_OAUTH_TOKEN`.
- Operations/admin API key usage remains separate from PSP OAuth and can continue for internal operators unless disabled by policy.

## Migration Steps for PSPs

1. Register the PSP client certificate through `POST /v1/participants/{pspId}/certificates/register`.
2. Request an OAuth token through `POST /v1/oauth/token`.
3. Send mutating API calls with:
   - `Authorization: Bearer <token>`
   - `X-Client-Cert: <URL-encoded PEM from the TLS edge>`
   - `X-Timestamp: <epoch seconds or ISO-8601 instant>`
   - `X-Request-Signature: <hex HMAC-SHA256>`
4. Stop sending `X-API-Key` for PSP payment traffic before the final cutover.
