# 66I — SMOS Runtime Security Certification

Use a short-lived UAT operator token. Validate authorization, bounded pagination, session behavior, MFA evidence and maker-checker separation.

## Evidence

Store runtime artifacts under `build/phase66-evidence/<run-id>/66I/`. Redact secrets before signing.

## Required lifecycle commands

Full mode requires `SMOS_PROVISION_INITIAL_ADMINS_COMMAND` and `SMOS_SECURITY_LIFECYCLE_COMMAND`. The latter must exercise TOTP, token refresh/revocation, session termination and maker-checker separation without printing credentials.
