# 18.01 API Key Lifecycle

Status: PASS
Tested at: 2026-07-16T09:17:07Z

Endpoints:
- GET /api/admin/api-keys
- POST /api/admin/api-keys
- POST /api/admin/api-keys/{id}/rotate
- POST /api/admin/api-keys/{id}/disable

Evidence:
- Admin listed existing API key metadata without exposing existing plaintext secrets.
- A new OPS key accessed a protected operations endpoint using mTLS plus X-API-Key.
- After rotation, the old key was rejected with HTTP 401 and the rotated key was accepted.
- After disable, the rotated key was rejected immediately with HTTP 401.
- Test plaintext secrets were not written to evidence files.

Conclusion:
API key creation, use, rotation, disable, and immediate revocation passed on UAT.
