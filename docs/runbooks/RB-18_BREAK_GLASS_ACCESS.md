# RB-18 — Break-Glass Access

Break-glass access never bypasses normal authentication or role checks. It adds a second requirement for
dead-letter replay execution, legal-hold release approval, and approved configuration execution.

1. OPS or ADMIN requests a session with incident/change ticket, reason, TTL (maximum 30 minutes), and
   usage cap (maximum 20).
2. A different ADMIN approves. The raw token is returned once; only SHA-256 and a display prefix are
   stored.
3. The token may be used only by the original requester, before expiry, and within the use cap.
4. Every use creates a chained audit event with action and token prefix, never the token.
5. Revoke immediately after work. Active sessions and unapproved requests older than 24 hours are
   marked expired by the scheduler, and every automatic transition is audit-chained.

Do not paste the token into tickets, chat, shell history, or evidence bundles.
