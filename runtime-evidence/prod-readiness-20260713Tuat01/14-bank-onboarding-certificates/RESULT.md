# 14 Bank Onboarding / Certificate Result

Status: PASS
Target: https://175.11.0.200
Test bank: EVD260716T02045 (INACTIVE)

Evidence:
- A disabled test participant, connector, and route were created through bank onboarding.
- A bank-generated CSR was issued as a client-auth certificate by the UAT issuer CA.
- The certificate was verified, registered, and accepted by mTLS on a protected operations endpoint.
- The certificate was revoked and its subsequent protected mTLS request was rejected with HTTP 401.
