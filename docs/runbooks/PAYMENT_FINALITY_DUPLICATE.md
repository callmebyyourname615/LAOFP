# Runbook — Payment Finality and Duplicate Protection

- For idempotency conflicts, compare the stored request hash. A different payload under the same key is rejected and must not be retried with force flags.
- For duplicate fingerprints, investigate participant, amount, currency, product, accounts, and time bucket before accepting a new transaction.
- A final payment is not edited. Exceptional reversal requires Operations and Risk approvals plus an independent executor.
- Reconcile ledger, liquidity, settlement, notifications, and participant statements after reversal.

Retain claim, fingerprint, finality, reversal, and reconciliation hashes in the incident evidence bundle.
