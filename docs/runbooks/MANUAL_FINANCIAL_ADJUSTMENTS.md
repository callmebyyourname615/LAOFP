# Runbook — Manual Financial Adjustments

Use only when automated reversal/refund/reconciliation paths cannot correct the ledger.

1. Confirm incident/case reference and business justification.
2. Build balanced debit/credit lines against existing control accounts.
3. Requester submits; an independent Finance approver decides; a third actor executes.
4. Verify the resulting `control_journal` is `POSTED` and balanced.
5. Reconcile transaction, settlement, participant statement, and general-ledger evidence.

Never update an executed adjustment or posted journal. Corrections require a new compensating adjustment referencing the original.
