# Phase 78 Operator Runbook

1. Merge authoritative Phase 61–77 source and migrations.
2. Run `scripts/phase78/run_phase78.sh --preflight`.
3. Freeze commit and image digests.
4. Execute Maven/migrations/UAT/operator/load/DR gates with explicit flags.
5. Sign attestations from original evidence only.
6. Run `--full` and verify the UAT closure bundle.
