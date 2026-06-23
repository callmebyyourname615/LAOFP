# Phase 63 runtime evidence

Phase 63 creates one immutable directory per run:

```text
evidence/phase63/<UTC-run-id>-<git-sha>/
```

Do not commit populated run directories containing environment metadata. Archive the approved directory in the controlled evidence store and retain `manifest.json` plus `SHA256SUMS` with the release record.
