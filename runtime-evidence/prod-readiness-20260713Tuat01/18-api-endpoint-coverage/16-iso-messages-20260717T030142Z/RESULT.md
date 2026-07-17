# 18.16 ISO Message Monitoring

Status: PASS

Evidence:
- ISO message list returned inbound `PACS_002` and outbound `PACS_008` message metadata.
- Message detail returned correlation identifiers and security metadata without exposing raw payloads.
- Security-policy lookup confirmed the expected inbound PACS.002 handling policy and compliance status.

Conclusion:
ISO message monitoring and message-specific security-policy inspection are available to UAT operations.
