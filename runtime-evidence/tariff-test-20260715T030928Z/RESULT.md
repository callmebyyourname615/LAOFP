# Fee / Tariff Test Result

Status: BLOCKED

Result:
GET /api/operations/tariffs works and returned an empty list.
POST /api/operations/tariffs returned 405 METHOD_NOT_ALLOWED.

Conclusion:
Fee / Tariff currently supports query/list only.
Create tariff API is not implemented yet.
Need to implement create, approve, activate tariff flow before UAT functional test.
