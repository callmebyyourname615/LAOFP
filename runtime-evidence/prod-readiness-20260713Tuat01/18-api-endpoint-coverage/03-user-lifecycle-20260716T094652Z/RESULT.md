# 18.03 User Lifecycle and RBAC Administration

Status: PASS

Endpoints verified:
- POST /api/admin/users
- GET /api/admin/users/{id}
- PUT /api/admin/users/{id}/roles
- PUT /api/admin/users/{id}/status

Evidence:
- A disposable UAT user was created as READ_ONLY and retrieved by id.
- The user was promoted to OPS_ADMIN and successfully accessed the operations health endpoint.
- The user was disabled after testing.
- Disabled-user login returned HTTP 401.
- The JWT issued before disable was rejected immediately with HTTP 401.

Conclusion:
User administration, RBAC role update, and immediate session revocation on disable passed on UAT.
