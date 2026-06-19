# Phase 54 Evidence Layout

```text
build/phase54-certification/
├── phases/
│   ├── 54A/ ... 54J/
│   │   ├── result.json
│   │   ├── checks.jsonl
│   │   ├── logs/
│   │   └── phase-specific evidence
├── attempts/
│   └── 54X-<timestamp>/
├── release-candidate/
│   ├── manifest.json
│   └── checksums.sha256
├── manifest.json
└── manifest.sha256
```

`manifest.json` is the complete certification decision. `release-candidate/manifest.json` binds prerequisite evidence to the immutable release before production approval. Both are evidence, not editable status documents.
