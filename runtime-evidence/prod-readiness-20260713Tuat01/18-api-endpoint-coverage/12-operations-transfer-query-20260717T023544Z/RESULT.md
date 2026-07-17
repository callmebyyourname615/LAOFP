# 18.12 Operations Transfer and Transaction Queries

Status: PASS

Endpoints verified:

- `GET /api/operations/transfers` returned filtered and paginated operational transfer records.
- `GET /api/operations/transfers/{transferRef}` returned the selected operational transfer detail.
- `GET /api/operations/transactions` returned the transaction monitor view.

Conclusion: Operations query endpoints returned UAT data with account numbers masked in list and detail responses.
