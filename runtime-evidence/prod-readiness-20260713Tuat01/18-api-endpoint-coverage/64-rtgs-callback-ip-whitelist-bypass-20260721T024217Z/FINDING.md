# CRITICAL: /v1/settlement/rtgs-callback IP whitelist provides no real protection

## Root cause (two independent bugs compounding)

1. **RtgsCallbackController.clientIp()** (src/main/java/com/example/switching/settlement/controller/RtgsCallbackController.java)
   trusts the client-supplied `X-Forwarded-For` header unconditionally, with no check that the
   request actually came through a trusted reverse proxy that sets/overwrites that header. Any
   external caller can set `X-Forwarded-For: <whitelisted-ip>` to bypass the whitelist entirely.

2. **Even without spoofing, the whitelist is already satisfied.** In the current single-node UAT
   topology, nginx terminates TLS and proxies to the app over `127.0.0.1`, so `request.getRemoteAddr()`
   for every external request already equals `127.0.0.1` — which is in the default whitelist
   (`RTGS_CALLBACK_IP_WHITELIST` defaults to `127.0.0.1,::1,0:0:0:0:0:0:0:1` per
   src/main/resources/application.yml:161). So the whitelist is a no-op regardless of bug #1.

3. Compounding config bug: docker-compose.yml passes `SWITCHING_SETTLEMENT_RTGS_CALLBACK_IP_WHITELIST`
   (line 301) but application.yml actually reads `RTGS_CALLBACK_IP_WHITELIST` (application.yml:161) —
   different variable name. Whatever whitelist ops configure in docker-compose is silently ignored;
   the app always falls back to the localhost-only default.

## Proven impact

`POST /v1/settlement/rtgs-callback` requires only a valid mTLS client certificate (any onboarded
participant's cert — no ADMIN/OPS JWT role required, SecurityConfig.java:176 marks this route
`permitAll()` at the JWT/role layer). With that alone, any participant can submit an RTGS
confirmation callback for **any instructionRef in the system**, moving it to CONFIRMED/FAILED at will —
this can forge settlement confirmations for cycles that never actually settled at the central bank.

Reproduced with a nonexistent instructionRef (`PROBE-NONEXISTENT-0001`) to avoid touching real
settlement data — both the unspoofed and XFF-spoofed calls reached business-logic validation
(404 instruction-not-found) instead of being rejected at 403 by the whitelist, proving the bypass
without mutating any real instruction. A follow-up test against a real self-created test instruction
(see 65-settlement-cycle-chain) further confirms end-to-end forgeability.

## Fix recommendation

- Do not trust `X-Forwarded-For` unless the direct TCP peer is the known reverse-proxy IP; prefer
  Spring's `ForwardedHeaderFilter` behind an explicit `server.forward-headers-strategy: framework`
  with nginx configured to strip any client-supplied XFF before setting its own.
- Fix the env var name mismatch so ops-configured whitelists actually take effect.
- Given mTLS alone is enough to reach this endpoint, consider requiring an HMAC/shared-secret
  signature from the actual RTGS gateway (BOL) in the callback body, not just source IP.
