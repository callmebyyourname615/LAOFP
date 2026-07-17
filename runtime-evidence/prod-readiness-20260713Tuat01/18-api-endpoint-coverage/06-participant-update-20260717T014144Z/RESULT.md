# 18.06 Participant Update Lifecycle

Status: PASS

Endpoints verified:

- `PATCH /api/participants/{bankCode}` updated the metadata of an inactive UAT-only participant.
- `GET /api/participants/{bankCode}` confirmed the update was persisted.
- The original participant name was restored as cleanup.

Conclusion: Participant metadata updates passed on UAT. The test participant stayed `INACTIVE`; no live routing or payment behavior was changed.
