# 17.04 Rate Limit and API Abuse Protection

Status: PASS_WITH_FIX

Evidence:
- Login abuse test exceeded the configured token bucket and returned HTTP 429.
- Response contained REQ-004, Retry-After, X-RateLimit-Limit, X-RateLimit-Remaining, and policy revision headers.
- UAT health remained UP after the test.

Fixes applied:
- Re-enabled UAT rate limiting with a 100 requests/minute policy.
- Fixed policy-service constructor injection so the configured/classpath policy is loaded rather than a one-request fallback.
- Made the Docker profile selectable through SPRING_PROFILES_ACTIVE instead of hardcoding dev in compose.

Conclusion:
API abuse protection is active and verified on UAT.
