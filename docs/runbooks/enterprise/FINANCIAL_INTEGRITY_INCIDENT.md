# Financial Integrity Incident

When Phase 57C returns `FREEZE_AFFECTED_SETTLEMENT`:

1. Stop settlement promotion for the affected business date and currency.
2. Preserve the repeatable-read snapshot and evidence hashes.
3. Open a SEV-1 incident with Financial Control as owner.
4. Do not manually change balances or journals.
5. Identify the first divergent transaction and its posting chain.
6. Reconcile ledger, transaction, settlement and outbox records.
7. Apply a reviewed compensating journal only through the approved accounting workflow.
8. Re-run Phase 57C and obtain Financial Controller approval before unfreezing.
