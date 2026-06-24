# SMOS Dashboard Integration Contract

| Navigation item | API | Required authority |
|---|---|---|
| Transactions | `/api/dashboard/transactions/summary` | `PERM_DASHBOARD_TRANSACTION` or settlement fallback |
| Participants | `/api/dashboard/participants` | `PERM_DASHBOARD_PARTICIPANT` or risk fallback |
| Infrastructure | `/api/dashboard/infrastructure` | `PERM_DASHBOARD_INFRASTRUCTURE` or risk fallback |
| DR readiness | `/api/dashboard/dr` | `PERM_DASHBOARD_DR` or infrastructure fallback |
| BAU status | `/api/operations/bau/status` | readiness/change-management authority |

All responses use no-store caching. The UI must hide navigation without authority and still rely on server-side authorization.
