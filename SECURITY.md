# Security Policy

## Reporting a vulnerability

Do not open a public issue containing credentials, customer data, payment data,
database exports, log excerpts, exploit details, or screenshots of sensitive
configuration. Use the repository's private security-advisory channel or the
organization's approved incident-management channel.

Include only the minimum metadata required to reproduce the issue. Never attach
real production secrets or database rows. Use synthetic identifiers and redact
all tokens, account numbers, phone numbers, personal data, and cryptographic
material.

## Repository security requirements

- Runtime secrets are injected from Vault/External Secrets and are never committed.
- `.env`, logs, database dumps, private keys, keystores, and generated evidence are prohibited.
- Pull requests must pass `Repository Hygiene`, `Gitleaks Secret Scan`, dependency,
  filesystem, container, and CodeQL gates.
- Scanner exceptions require an owner, reason, narrow scope, and expiry date.
- Any committed credential is treated as exposed even after the file is deleted.
  Rotate/revoke it, purge Git history, and preserve redacted incident evidence.

See `docs/security/REPOSITORY_SECURITY_CLEANUP.md` for the operational procedure.
