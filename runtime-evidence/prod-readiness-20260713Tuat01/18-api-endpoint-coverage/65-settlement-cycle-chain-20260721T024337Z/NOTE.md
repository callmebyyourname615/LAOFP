# Settlement-cycle mutation group — PARTIALLY testable, blocked by data availability

## What we confirmed
- `POST /api/operations/settlement/cycles` — endpoint reachable and enforces its
  "max 4 cycles per day" business rule correctly (409 SET-002). Valid negative/validation evidence.
- `POST /api/operations/settlement/cycles/{cycleRef}/instructions/generate` — 200, correctly
  returns an empty list when the cycle has no batched items.

## What we could NOT test today (2026-07-21 UTC)
- `POST .../cycles/{ref}/batch` — needs an OPEN cycle; all 4 of today's auto-scheduled cycles
  (SC-20260721-C1..C4) are already CLOSED with itemCount=0.
- `POST .../cycles/{ref}/close` — same blocker; nothing left in OPEN state to close.
- `POST .../cycles/{ref}/settle` — needs approved+confirmed instructions; none exist.
- `POST .../instructions/{ref}/approve` — needs a PENDING_APPROVAL instruction; none exist anywhere
  in the system right now (checked GET .../cycles?status=CLOSED, all itemCount=0).
- `POST .../instructions/{ref}/reject` — same blocker.
- `POST .../instructions/{ref}/send-rtgs` — same blocker.
- `POST .../instructions/{ref}/record-rtgs-upload` — same blocker.

Root cause: this UAT instance auto-opens/closes 4 DNS cycles/day on a scheduler
(~01:45, 04:45, 08:15, 12:45 UTC observed), but no payment traffic ran during today's OPEN
windows, so nothing was ever batched. Prior evidence (07-settlement, 2026-07-13) exercised this
full chain successfully when real settled transfers existed in the batching window.

## To finish this group
Either (a) re-run after a fresh UTC day resets the 4-cycle quota and trigger/wait for the next
OPEN window while driving a real transfer through the payment happy path so it lands in that
cycle, or (b) if the daily-cycle cap and scheduler timing make same-day testing impractical,
treat 07-settlement's 2026-07-13 run as the happy-path evidence of record for this chain and
scope this group's remaining UAT-01 evidence to the negative/validation cases collected here.
