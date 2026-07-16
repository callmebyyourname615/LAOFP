# 17.02 RBAC Authorization Matrix

Status: PASS_WITH_FIX

Evidence:
- READ_ONLY user can access participant read API.
- READ_ONLY user is denied access to user management with HTTP 403.
- A least-privilege defect was found: READ_ONLY and other specialist roles inherited ROLE_OPS.
- The defect was fixed by restricting ROLE_OPS to OPS_ADMIN and SYSTEM_ADMIN only.
- Retests confirm READ_ONLY is denied break-glass and configuration-change creation with HTTP 403.
- The test break-glass request was revoked.

Conclusion:
Role-based authorization now enforces read-only and privileged-operation boundaries on UAT.
