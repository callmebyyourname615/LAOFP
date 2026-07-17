# 18.28 Settlement Balance Read

Status: PASS

Evidence:
- System administrator retrieved the SUNDAYBANK liquidity pool balance using an explicit `pspId`.
- The response included balance, held amount, available balance, currency, minimum balance, and update timestamp.
- Omitting `pspId` as an administrator returns a controlled HTTP 400 validation error instead of an internal error.
- Pool history returned recent HOLD and CONFIRM records with before/after liquidity balances.
- Settlement positions returned balanced debit and credit net positions for the selected cycle.

Conclusion:
Settlement balance reads now resolve administrative access safely and require explicit PSP selection.
