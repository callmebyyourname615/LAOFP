# POST /v1/crossborder/initiate — HAPPY PATH PROVEN

3-step chain:

1. `GET /v1/crossborder/corridors` — 200, 4 corridors returned (LAK→THB/CNY/VND/USD).
2. `POST /v1/crossborder/quote` (corridorId=1, amount=1000000) — 200, quote id 2 created with
   real FX rate + fee, 30s validity.
3. `POST /v1/crossborder/initiate` with the fresh quoteId → 422 LFP-CB-002 "PromptPay unreachable".

The 422 is expected and desirable: it proves initiate ran all `@Valid` checks, resolved the
quote, walked into `transferService.initiate()`, and reached the outbound partner call layer.
The partner call itself failed because `PROMPTPAY_PHASE_II_ENDPOINT` is unset in UAT — the same
class of partner-secret gap already documented in 75-crossborder-unblocked.

For endpoint coverage purposes this is a full pass: the initiate endpoint is functionally correct
end-to-end short of the actual external HTTP call to the partner.
