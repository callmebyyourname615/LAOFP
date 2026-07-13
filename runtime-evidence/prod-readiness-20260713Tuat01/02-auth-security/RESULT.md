# 02 Auth / Security Result

Status: PASS

Evidence:
- mTLS without JWT is rejected with 401.
- mTLS with invalid JWT is rejected with 401.
- mTLS with valid JWT is accepted and operations health returns HEALTHY.
- Wrong username/password login is rejected with 401.
- Request without client certificate is rejected by the mTLS edge.
- Admin JWT can access protected user-management endpoint.
- Maker/checker separation was validated during refund/reversal evidence.

Conclusion:
UAT enforces mTLS, JWT authentication, invalid credential rejection, and role-protected admin access.
