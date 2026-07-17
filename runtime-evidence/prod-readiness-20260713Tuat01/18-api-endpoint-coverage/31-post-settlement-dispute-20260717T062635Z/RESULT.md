# 18.31 Post-Settlement Dispute

Status: PASS

Evidence:
- Selected an in-window transfer in `SETTLED` status.
- Opened a `POST_SETTLEMENT_DESTINATION_DISPUTE` for UAT endpoint coverage.
- Verified that the transfer remained `SETTLED` while its confirmation and settlement-confidence states became `DISPUTED`.
- Verified no refund was created.
- Repeated the same request and received controlled HTTP `409`, `LFP-9003`, proving that an active dispute cannot be duplicated for a transfer.
- Submitted and checker-approved a `NO_ACTION` resolution. The dispute became `RESOLVED_NO_ACTION` and the settled transfer was restored to `CONFIRMED` without a refund.
- Corrected a production defect found during the test: transaction reference text is now truncated to its 100-character database limit while the full decision note remains in dispute evidence and audit records.
