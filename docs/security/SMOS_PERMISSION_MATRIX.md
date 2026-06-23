# SMOS Permission Matrix

All permissions are deny-by-default. `PARTICIPANT_ADMIN` is additionally restricted
by the `participantId` claim and must never inherit payment-path PSP authorities.
Sensitive write actions are submitted through maker-checker and cannot be approved
by the maker.

| Role | User admin | Settlement | Dispute | Risk | Participant | Audit | Dashboards | Maker-checker |
|---|---|---|---|---|---|---|---|---|
| SYSTEM_ADMIN | view/manage | view/approve | view/resolve | view/investigate | view/manage | read | all | submit/approve |
| OPS_ADMIN | view | view/approve | view/resolve | view/investigate | view/manage | read | all | submit/approve |
| SETTLEMENT_OFFICER | none | view/approve | none | none | none | none | settlement | submit/approve |
| DISPUTE_OFFICER | none | none | view/resolve | none | none | none | none | submit/approve |
| RISK_OFFICER | none | none | none | view/investigate | none | none | risk | submit/approve |
| AUDITOR | view | view | view | view | view | read | all | none |
| PARTICIPANT_ADMIN | none | none | none | none | own participant only | none | none | submit |
| READ_ONLY | none | view | view | view | view | none | read-only dashboards | none |

## Endpoint controls

| Endpoint | Required control |
|---|---|
| `POST /api/admin/users` | SYSTEM_ADMIN; audit event |
| `PUT /api/admin/users/{id}/roles` | SYSTEM_ADMIN; active sessions revoked |
| `PUT /api/admin/users/{id}/status` | SYSTEM_ADMIN; sessions revoked when disabled |
| `POST /api/admin/requests` | `PERM_MAKER_CHECKER_SUBMIT` |
| `POST /api/admin/requests/{id}/approve` | `PERM_MAKER_CHECKER_APPROVE`; maker != checker; payload hash verified |
| `GET /api/dashboard/settlement` | `PERM_DASHBOARD_SETTLEMENT`; scheme-wide operator only |
| `GET /api/dashboard/risk` | `PERM_DASHBOARD_RISK`; scheme-wide operator only |
| `GET /api/dashboard/cross-border` (legacy alias `/crossborder`) | `PERM_DASHBOARD_CROSSBORDER`; scheme-wide operator only |
| `GET/DELETE /api/auth/sessions` | own sessions; `PERM_SESSION_VIEW/REVOKE` |

## Read-your-writes policy

Authentication, role changes, maker-checker decisions, settlement state transitions,
limits, balances and idempotency checks must use primary. Dashboards and historical
reports may use replica-backed read-only transactions.
