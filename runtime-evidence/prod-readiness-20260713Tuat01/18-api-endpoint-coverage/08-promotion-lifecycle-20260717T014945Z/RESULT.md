# 18.08 Promotion Lifecycle

Status: PASS

Endpoints verified:

- `POST /v1/promotions` created an isolated `DRAFT` promotion.
- `GET /v1/promotions` returned the draft.
- `PATCH /v1/promotions/{id}/extend` moved the end date forward.
- `GET /v1/promotions/{id}/report` returned zero usage and the updated promotion state.
- `DELETE /v1/promotions/{id}` deleted the draft; the list endpoint confirmed cleanup.

Conclusion: Draft promotion lifecycle passed on UAT. The test promotion was never activated, never applied to payment, and was removed after testing.
