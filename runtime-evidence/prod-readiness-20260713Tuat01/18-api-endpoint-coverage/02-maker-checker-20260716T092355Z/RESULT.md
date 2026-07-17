# 18.02 Maker-Checker Safe Lifecycle

Status: PASS (submit, pending query, reject, and self-approval guardrail)

Endpoints verified:
- POST /api/admin/requests
- GET /api/admin/requests
- POST /api/admin/requests/{id}/reject
- POST /api/admin/requests/{id}/approve (negative self-approval guardrail)

Evidence:
- Maker submitted a non-executable settlement approval request.
- A separate checker listed the pending request and rejected it without executing a settlement action.
- The maker's self-approval attempt was rejected with HTTP 400 and REQ-001.
- The remaining test request was rejected by the separate checker as cleanup.

Notes:
- The approval endpoint's successful settlement-instruction execution remains NOT_TESTED. It requires a controlled pending settlement instruction and should be run under the settlement bundle.
