# Key rotation and audit integrity

Vault Transit key rotation is exercised with `security/scripts/vault-transit-key-rotation-drill.sh`. The drill advances the key version and queues a webhook delivery to prove old envelope ciphertext remains decryptable. The operator token must be short-lived and scoped only to read/rotate the named key.

New audit entries are sanitized, serialized, chained with SHA-256, and inserted while holding a PostgreSQL advisory transaction lock. The application database role receives only SELECT/INSERT on `audit_logs`; UPDATE/DELETE/TRUNCATE are revoked. Run `scripts/harden_audit_permissions.sh` after migration and `security/scripts/verify-audit-chain.sh` during readiness review.
