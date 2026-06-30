# Auto Retry Runtime Evidence

Run ID: auto-retry-20260629-163320
Inquiry Ref: INQ-20260629093344-2DDFDDAC
Transfer Ref: TRX-20260629093344-1D1E7E1B

## Expected Result

- Initial source-bank response:
  - status = ACCEPTED
  - result = OK
  - resultDetail = PENDING

- Auto retry:
  - outbox retryCount increases automatically
  - no manual retry endpoint used

- After destination connector restored:
  - PACS_002 INBOUND exists
  - transfer status = READY_FOR_SETTLEMENT
  - result = OK
  - resultDetail = OK

## Evidence Files

- 03-create-transfer-accepted-pending.json
- 04-source-view-accepted-ok-pending.json
- 06-outbox-after-auto-retry-1.json
- 09-trace-after-auto-retry-success.json
- 10-source-view-ready-ok-ok.json
