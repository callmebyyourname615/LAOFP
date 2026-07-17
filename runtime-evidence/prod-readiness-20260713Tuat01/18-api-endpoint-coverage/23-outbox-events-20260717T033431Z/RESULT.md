# 18.23 Outbox Event List

Status: PASS_WITH_NOTES

Evidence:
- Outbox event listing filtered successfully by `SUCCESS` status.
- Results exposed transfer reference, message type, final status, and retry count.
- Recovery test events showed expected successful retry counts.

Note:
- The list contract uses `limit`; unsupported `page` and `size` query parameters fall back to the default limit of 50.

Conclusion:
Outbox monitoring supports operational filtering and retry visibility.
