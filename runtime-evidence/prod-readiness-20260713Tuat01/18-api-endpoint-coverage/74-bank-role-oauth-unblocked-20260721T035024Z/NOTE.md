# BANK-role endpoints unblocked via OAuth (SECURITY_OAUTH_ENABLED=true redeployed)

After the docker-compose fix + UAT redeploy, PSP OAuth authentication now works end-to-end:
`POST /v1/oauth/token` issues a Bearer token, and `OAuthTokenFilter` validates it on downstream
requests, mapping the caller to `ROLE_BANK`.

## Endpoints exercised with BANK_A OAuth token

| Endpoint | Result |
|---|---|
| POST `/v1/settlement/liquidity/topup` | **PASS 200** — real happy path, credited pool +10000 LAK |
| POST `/v1/qr/pay` | PASS 404 business (QR not found) |
| POST `/v1/qr/refund` | PASS 404 business (txn not found) |
| POST `/v1/webhooks` | PARTIAL — auth OK, blocked at allowlist (see below) |
| GET  `/v1/webhooks` etc. — read + secret/rotate + /test + DELETE | Not exercised due to registration block |

## Follow-ups (worth logging, not blocking endpoint coverage)

- `WEBHOOK_ALLOWED_HOSTS` is empty in UAT `.env` and container env, yet the runtime effectively
  rejects `example.com` with "not in the outbound allowlist". This means either an active profile
  overrides the default empty list, or the effective policy path is different from the one that
  appears empty at Spring-bind time. Worth a config audit before production.
- Combined with the OAuth rotation race in 72 and the OAuth-filter-disabled bug in 73, the PSP
  integration surface has three separate config-layer defects that all need to close before any
  PSP can integrate cleanly. Report Delivery deployment work also flagged: signing secret unset,
  and MissingServletRequestParameter → 500.
