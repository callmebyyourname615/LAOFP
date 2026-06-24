# Phase 75 Operator Runbook

1. Confirm Phase 74J is PASS and freeze the release identity.
2. Execute 75A–75G in UAT using the Phase 54 runner.
3. Do not rebuild images between acceptance phases.
4. Execute 75H only against a production-like replica or approved production dry-run target.
5. Verify all canary abort conditions and rollback commands during 75I.
6. Collect Engineering, QA, Security, SRE, Product and Change Manager approvals.
7. Run 75J; schedule production cutover only when the decision is GO.
