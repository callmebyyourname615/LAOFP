# Post-settlement Destination Dispute Evidence

TRANSFER_REF=TRX-20260630090320-490D9697

Expected:
- Transfer remains SETTLED
- confirmationStatus = DISPUTED
- settlementConfidence = DISPUTED
- disputeType = POST_SETTLEMENT_DESTINATION_DISPUTE
- dispute status = OPEN
- duplicate dispute returns LFP-9003 / 409 CONFLICT

Files:
- 00-context.txt
- 01-transfer-after-post-settlement-dispute.json
- 02-db-dispute.txt
- 03-audit-post-settlement-dispute.txt
- 04-duplicate-dispute-rejected.json
