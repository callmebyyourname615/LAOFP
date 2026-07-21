# Settlement cycle chain — batch/close/instructions-generate now PASS

Cycle SC-20260722-C3 opened for a future settlementDate (2026-07-22) since today's quota was
exhausted. All happy-path chain endpoints returned 200/expected error:

- POST /api/operations/settlement/cycles                           — 200 (open)
- POST /api/operations/settlement/cycles/{ref}/batch               — 200 (0 items, no transactions to batch)
- POST /api/operations/settlement/cycles/{ref}/close               — 200 (OPEN → CLOSED)
- POST /api/operations/settlement/cycles/{ref}/instructions/generate — 200 (empty list, no positions to net)
- POST /api/operations/settlement/cycles/{ref}/settle              — 409 SET-002 (guard fired: no confirmed instructions)

## Endpoints still not exercised
- POST .../instructions/{ref}/approve
- POST .../instructions/{ref}/reject
- POST .../instructions/{ref}/send-rtgs
- POST .../instructions/{ref}/record-rtgs-upload

Reason: needs at least one settlement instruction, which requires real net positions in the
cycle, which requires SETTLED transfers batched into the cycle. No such transfers exist today.

## Design smell (worth flagging, not critical)
`POST .../cycles` accepts a future settlementDate (2026-07-22) and allows immediate batch+close
even though transactions for that business date have not occurred yet. There is no guard
"cannot close a cycle whose settlementDate has not arrived" or similar. In production this could
allow an operator to accidentally close a future cycle empty, preventing that day's real
settlement from happening in the intended cycle. Consider guarding
`SettlementCycleService.closeCycle()` and/or `openCycle()` against future dates, or documenting
the intended behavior if this is deliberate for backfill scenarios.
