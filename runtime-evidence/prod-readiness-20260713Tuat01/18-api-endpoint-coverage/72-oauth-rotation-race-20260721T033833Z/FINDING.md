# BUG: OAuth token issuance rejects a brand-new token when it is issued in the same second as a credential rotation

## Reproduction
1. `POST /v1/participants/BANK_A/credentials/rotate` (as ADMIN) — returns new
   `clientId` + `clientSecret`, and internally calls
   `OAuthTokenService.markClientRotated(clientId, now)`.
2. Immediately `POST /v1/oauth/token` with `grant_type=client_credentials` +
   the fresh credentials.
3. Expected: 200 with a Bearer token.
4. Observed: 401 `LFP-2001 "Token invalidated by credential rotation"`.

Adding `sleep 3` between step 1 and step 2 makes it succeed. Reproduced live on UAT
at 2026-07-21T03:38:33Z — see this folder's 01-rotation-race-error.json vs
02-rotation-race-workaround.json.

## Root cause

`src/main/java/com/example/switching/security/oauth/controller/OAuthTokenController.java:100-102`
```
// We call validateToken here only to read the claims — at this point the
// token is brand new and guaranteed valid, so no exception can be thrown.
OAuthTokenClaims claims = tokenService.validateToken(accessToken);
```
The comment is wrong. `validateToken` at
`src/main/java/com/example/switching/security/oauth/service/OAuthTokenService.java:134-137`
rejects any token whose `iat <= rotationEpoch`:

```
Long rotationEpoch = clientRotationEpochs.get(clientId);
if (rotationEpoch != null && iat <= rotationEpoch) {
    throw new OAuthTokenInvalidException("Token invalidated by credential rotation");
}
```

`markClientRotated` stores `rotationEpoch = now` at rotation time. `createToken`
sets `iat = now`. When the rotate call and the issue call land in the same
second, `iat == rotationEpoch`, so `iat <= rotationEpoch` is true, and the
brand-new token — which the comment above the call promises "cannot throw" —
is rejected 401 immediately.

## Impact

- Any PSP that rotates its credential and immediately tries to obtain a Bearer
  token (as an automated `rotate → refresh token → resume traffic` pipeline
  would) is locked out for a fraction of a second up to a full second. In
  automation this is race-prone and non-deterministic; in a stress event
  (multiple rotations, clock skew) it can fail retries too.
- Any human operator who follows the natural flow "rotate, then log in with
  new secret" hits the same wall until they retry.
- The 401 message names an integrity condition ("Token invalidated by
  credential rotation") that has no meaning to the caller here — the token
  was born after the rotation.

## Recommended fix (choose one)

1. Compare strictly: `iat < rotationEpoch` in
   `OAuthTokenService.validateToken`. Semantically correct: only tokens
   strictly older than the rotation are invalidated.
2. Set `rotationEpoch = now - 1` in `markClientRotated`, keeping the ≤
   check. Slightly cheaper, but less obvious.
3. Skip the rotation check in the internal `validateToken` call made from
   `OAuthTokenController.token()` (the comment claims this call is only for
   claim readback). Restores the "cannot throw" invariant the comment
   promises.

Option 1 is the clearest for anyone reading the code later.
