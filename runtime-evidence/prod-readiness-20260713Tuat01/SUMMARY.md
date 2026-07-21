# Switching UAT Production Readiness — Decision Brief

Bundle: `prod-readiness-20260713Tuat01`
Target: `https://175.11.0.200`
Last full pass: **2026-07-21** (this document supersedes the 2026-07-16 draft)

## Verdict

**NOT READY FOR PRODUCTION — hard stop on the RTGS callback bypass.**

Score: **72 / 100** (down from the earlier 91/100 draft as several critical findings not
previously verified were confirmed live on UAT this pass).

Once the four items in "🔴 Must fix before ship" are closed, revisit for a re-score.

| Dimension | Status |
| --- | --- |
| API endpoint coverage | 241 / 241 verified live on UAT |
| Payments happy path (core) | Passing since 2026-07-13 |
| Settlement chain (core) | Passing (mock RTGS flow) |
| Phase II — RTP | Deployed mid-session, 4/6 full happy path + 1 code bug |
| Phase II — Report Delivery | Deployed mid-session, 4/4 route pass; secret unset |
| Phase II — Cross-Border | Deployed mid-session, initiate + reconciliation happy path; per-rail secrets unset |
| PSP OAuth authentication | Wired up mid-session (was silently disabled); 1 rotation race bug |
| **Settlement RTGS callback** | **🔴 IP whitelist provides no real protection** |

## 🔴 Must fix before ship

1. **`POST /v1/settlement/rtgs-callback` IP whitelist bypass** — trusts client-supplied
   `X-Forwarded-For` unconditionally, and every external request already appears as `127.0.0.1`
   through the nginx edge, so the whitelist is a no-op. Combined with `permitAll()` at the JWT
   layer, any participant holding an mTLS client cert can forge RTGS settlement confirmations for
   any `instructionRef`. Evidence:
   `18-api-endpoint-coverage/64-rtgs-callback-ip-whitelist-bypass/FINDING.md`.
   **Fix:** validate `X-Forwarded-For` only when direct TCP peer is the trusted proxy IP; add an
   HMAC/shared-secret signature from the RTGS gateway to the callback body; fix the env-var name
   mismatch (`SWITCHING_SETTLEMENT_RTGS_CALLBACK_IP_WHITELIST` in compose vs
   `RTGS_CALLBACK_IP_WHITELIST` in application.yml).

2. **PSP OAuth rotation race** — `OAuthTokenService.validateToken` rejects tokens whose `iat` is
   `<= rotationEpoch`. Because rotation and the immediate token-issue call can land in the same
   wall-clock second, brand-new tokens are 401'd. `OAuthTokenController.token()`'s comment claims
   the internal `validateToken` call "cannot throw" — it can. Evidence:
   `18-api-endpoint-coverage/72-oauth-rotation-race/FINDING.md`.
   **Fix:** compare `iat < rotationEpoch` strictly (option 1 in the finding).

3. **PSP OAuth filter was silently disabled** — until we redeployed with
   `SECURITY_OAUTH_ENABLED=true` this session, `POST /v1/oauth/token` was minting tokens that no
   downstream filter validated, so every subsequent call landed on the anonymous auth path (401
   SEC-001). Evidence: `18-api-endpoint-coverage/73-oauth-filter-disabled/FINDING.md`.
   **Fix:** the flag is now on in UAT, but startup should refuse to run (or WARN loudly) when the
   token endpoint is registered while `oauthEnabled=false`.

4. **`REPORT_LINK_SIGNING_SECRET` and per-rail cross-border partner API keys are unset in
   UAT `.env`.** The report-delivery and cross-border inbound features are gated on flags that
   are now `true`, but the secrets those features need to actually operate are empty. Any
   generated download link 400s; any partner inbound push 400s. Evidence:
   `71-report-delivery-unblocked` and `75-crossborder-unblocked`.
   **Fix:** provision the secrets; add a `ProductionStartupValidator` check that refuses to boot
   with a flag on and its secret blank.

## 🐛 Non-blocking code defects (should fix in the next patch)

- **`AuthoriseRtpRequest.inquiryRef` contract mismatch** — DTO marks it optional, service
  requires it, and the error message says "Required value is blank" without naming the field.
  Evidence: `69-rtp-happy-path/BUG-inquiryRef-contract-mismatch.md`. **Fix:** annotate
  `@NotBlank` or make it truly optional in the service.
- **`GlobalExceptionHandler` maps `MissingServletRequestParameterException` and
  `MissingRequestHeaderException` to 500 SYS-001.** Should be 400 with the missing name.
  Confirmed on `GET /v1/reports/download/{id}` (query) and
  `POST /v1/crossborder/inbound/{rail}` (headers).
- **Data-truncation bug on `transactions.reference` (or similar varchar(100))** — outbox event
  62 (`TRX-20260716043110-B32607EF`) stuck FAILED since 2026-07-16 with
  `value too long for type character varying(100)`. Evidence:
  `63-outbox-retry-mark-reviewed/NOTE.md`.
- **Webhook `outbound-allowlist` is empty but still rejects** — the effective policy in the
  container is stricter than the `.env`/application.yml default suggests. Root cause not fully
  traced this session; blocks any real webhook lifecycle test. Evidence:
  `74-bank-role-oauth-unblocked/NOTE.md`.
- **Settlement cycle `openCycle`/`closeCycle` allow future dates with no data.** Not
  security-critical, but operators can accidentally close a future cycle empty. Evidence:
  `70-settlement-cycle-chain-future-date/NOTE.md`.

## ✅ What actually works end-to-end today

- Auth, mTLS, JWT rejection, RBAC (verified in `02-auth-security`, `17-security-operational`).
- Payment happy path, retry, refund/reversal, settlement chain, outbox recovery
  (`03-payment-happy-path`, `04-payment-failure-retry`, `05-refund-reversal`, `07-settlement`,
  `08-outbox-recovery`).
- 4 outbox operator endpoints (retry / mark-reviewed / retry-all) — real happy path
  (`63-outbox-retry-mark-reviewed`).
- Credential rotation (`66-credentials-rotate`).
- Maker-checker submit/approve/reject with a proper 2nd-admin flow
  (`76-maker-checker-approve-happy`).
- 4 RTP endpoints (create/get/cancel/decline) — full happy path
  (`69-rtp-happy-path`).
- Report Delivery schedule GET/POST/PATCH — full route + validation
  (`71-report-delivery-unblocked`).
- Cross-border corridors + FX quote + initiate — chained happy path to partner-call layer
  (`77-crossborder-initiate-chain`); reconciliation with an empty statement
  (`75-crossborder-unblocked`).
- BANK-role liquidity top-up (real +10,000 LAK credit to BANK_A pool), QR pay, QR refund
  (`74-bank-role-oauth-unblocked`).
- Settlement instruction mutation endpoints (approve/reject/send-rtgs/record-rtgs-upload) — route
  + auth + business layer proven via probe (`78-remaining-probes`).
- Dead-letter mutation endpoints — route + auth verified; execute/discard correctly require a
  break-glass token (`78-remaining-probes`).

## 📉 Blocked by test-data availability, not code

- Settlement instruction happy path (a real settled transfer needs to enter a batchable cycle).
- RTP `authorise` + `settlements` happy path (needs a real transfer-inquiry record via
  ISO20022 inquiry flow).
- Full webhook lifecycle end-to-end (needs a host on the outbound allowlist).
- Dead-letter execute/discard destructive ops (needs a valid break-glass token and a real
  quarantined message).

These are not endpoint defects — the endpoints are wired correctly and were reached with
identity + validation intact. Add them to the go-live smoke-test plan once prod fixtures exist.

## Deployment fixes applied this session

- `docker-compose.yml` extended to forward `PHASE_II_RTP_ENABLED`,
  `PHASE_II_REPORT_DELIVERY_ENABLED`, `PHASE_II_CROSS_BORDER_ENABLED` (+ per-rail secrets),
  `SECURITY_OAUTH_ENABLED`, `OAUTH_TOKEN_TTL_SECONDS`.
- `PHASE_II_REPORT_DELIVERY_ENABLED=true` and `SECURITY_OAUTH_ENABLED=true` were flipped on the
  UAT `.env` and the app container was redeployed. Twice.
- `PHASE_II_CROSS_BORDER_ENABLED=true` was flipped on the UAT `.env` and redeployed again.
- The RTP flag was already true on UAT when this pass started; earlier evidence shows the module
  came live between 01:43Z and 03:01Z on 2026-07-21.

## Sign-off checklist before production cut

- [ ] RTGS callback bypass fixed and re-verified with a forged `X-Forwarded-For` probe.
- [ ] OAuth rotation race fixed and re-verified with rotate→immediate-issue in the same second.
- [ ] Startup guard added for `oauthEnabled=false` while `/v1/oauth/token` is registered.
- [ ] `REPORT_LINK_SIGNING_SECRET` provisioned (≥ 32 chars, generated by ops).
- [ ] Per-rail inbound partner API keys (`PROMPTPAY_INBOUND_API_KEY`, `BAKONG_INBOUND_API_KEY`,
      `NAPAS_INBOUND_API_KEY`, `UPI_INBOUND_API_KEY`) provisioned for whichever rails go live.
- [ ] Cross-border partner endpoints (`PROMPTPAY_PHASE_II_ENDPOINT` etc.) provisioned for
      whichever rails go live.
- [ ] Webhook outbound allowlist populated with actual PSP domains; verified with a real
      register/test/delete cycle.
- [ ] Outbox event 62 investigated for varchar(100) truncation and fixed.
- [ ] Fresh full regression + go-live runbook rehearsal.

## Full evidence index

- Endpoint matrix (241/241): `18-api-endpoint-coverage/ENDPOINT_EVIDENCE_MATRIX.md`
- Machine-readable CSV: `18-api-endpoint-coverage/endpoint-evidence-matrix.csv`
- Findings and probe evidence: `18-api-endpoint-coverage/60-*` through `78-*` folders.
- Machine-readable score: `11-score.json`
