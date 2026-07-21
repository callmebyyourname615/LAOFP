# CRITICAL: OAuth authentication path is half-open — token minting works, token validation is disabled

## Symptom
- Fresh OAuth Bearer token (obtained successfully from `POST /v1/oauth/token` after credential
  rotation) is presented on every subsequent `Authorization: Bearer …` request.
- All 6 BANK-role endpoints tested (`liquidity/topup`, `qr/pay`, `qr/refund`, `webhooks` CRUD,
  `webhooks/*/test`, `webhooks/*/secret/rotate`) uniformly return **401 SEC-001 "Missing or
  invalid authentication credential"** — not LFP-2001 that `OAuthTokenFilter` would emit.

## Root cause
`SecurityConfig.oauthEnabled` is derived from `switching.security.oauth.enabled`
(`application.yml:172-173`), which defaults to `false` and is bound to
`${SECURITY_OAUTH_ENABLED:false}`. The `SECURITY_OAUTH_ENABLED` variable is **not present in
`docker-compose.yml`** at all — so the container starts with `oauthEnabled = false`, and
`OAuthTokenFilter` is never added to the security chain (SecurityConfig lines 100-106).

Meanwhile `OAuthTokenController` (`/v1/oauth/token`) is an unconditional `@RestController` — it
happily issues signed Bearer tokens no matter what the flag says. Result: minting works, validation
does not exist, and no BANK-role endpoint is reachable via OAuth.

## Impact
- No PSP can call any BANK-role endpoint via the documented OAuth path. Payments, QR pay/refund,
  liquidity top-up, and every webhook lifecycle endpoint are unreachable to legitimate
  participants right now (unless they hold an X-API-Key alternative).
- Any evidence that OAuth issuance "works" is misleading because the token cannot be used.
- Combined with the rotation race bug in `72-oauth-rotation-race`, PSP OAuth integration is
  currently non-functional end-to-end.

## Recommended fix
1. Add `SECURITY_OAUTH_ENABLED=true` to `docker-compose.yml` (and `.env`) and redeploy.
2. Startup should refuse to run — or at minimum, WARN loudly — when `/v1/oauth/token` is
   registered but `oauthEnabled = false`. Otherwise this whole failure mode is silent.

## Reproduction data
- BANK_A OAuth token obtained via `client_credentials`, length 311, signature valid.
- Tried on: `/v1/settlement/liquidity/topup`, `/v1/qr/pay`, `/v1/qr/refund`, `POST/GET/DELETE
  /v1/webhooks`, `POST /v1/webhooks/{id}/test`, `POST /v1/webhooks/{id}/secret/rotate`.
- All returned identical 401 SEC-001 with path echoed correctly (proving the request reached
  Spring Security's authorization layer with no identity attached).
