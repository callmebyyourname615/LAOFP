# Phase II Scheduled Report Delivery Runbook

## SFTP

- Private-key authentication only.
- Strict host-key checking is mandatory.
- The known-hosts file must be provisioned out of band.
- Upload uses a temporary name followed by atomic rename.

## S3/MinIO

- Use HTTPS except loopback-only tests.
- The destination bucket must already exist.
- Credentials are environment references and must not be stored in schedule JSON.
- Object prefix and file name are validated against path traversal.

## Email link

- Signing secret must contain at least 32 bytes.
- Public base URL must use HTTPS except loopback tests.
- Link lifetime is 24 hours.
- Artifact expiry is checked again during download.

## Retry and dead-letter

Delivery claims are persisted before network I/O. Stale claims return to retry.
Retries use bounded exponential backoff and move to `DEAD` after five attempts.
Do not regenerate an artifact when redelivering the same generation key.
