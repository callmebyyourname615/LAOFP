# Runbook — Cryptographic Asset Inventory and Rotation

1. Inventory only provider references/fingerprints; never paste a private key, HMAC secret, password, or token.
2. Confirm all service bindings and trust consumers before rotation.
3. Define overlap and rollback windows; test decrypt/verify with old and new versions where protocol permits.
4. Use separate requester, approver, and executor.
5. Upload non-secret evidence: fingerprints, provider audit IDs, test output hash, and completion time.
6. Revoke/retire the old asset after overlap and successful production verification.

Overdue/expired critical assets are a release blocker unless an approved, expiring risk exception exists.
