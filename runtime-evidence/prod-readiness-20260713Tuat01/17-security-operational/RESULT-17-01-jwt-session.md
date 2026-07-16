# 17.01 JWT and Session Security

Status: PASS

Evidence:
- Login issues access and refresh tokens.
- Refresh token rotation issues a new refresh token.
- Reuse of an old refresh token is rejected and revokes its token family.
- Logout revokes its refresh token.
- Admin session revoke invalidates both the refresh token and the associated access JWT immediately.

Fix applied:
- SMOS access JWT now carries a session-family claim.
- Every bearer-token request validates that its session family remains active.
- Revoked sessions therefore return 401 immediately instead of remaining usable until JWT expiry.

Conclusion:
JWT/session lifecycle and immediate session revocation controls passed on UAT.
