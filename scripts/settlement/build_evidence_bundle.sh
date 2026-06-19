#!/usr/bin/env bash
set -euo pipefail
: "${SETTLEMENT_CYCLE_ID:?SETTLEMENT_CYCLE_ID required}"
: "${INPUT_DIR:?INPUT_DIR required}"
OUT="${OUTPUT_DIR:-evidence/settlement}/${SETTLEMENT_CYCLE_ID}"
mkdir -p "$OUT"
find "$INPUT_DIR" -type f -maxdepth 1 -print0 | sort -z | xargs -0 sha256sum > "$OUT/SHA256SUMS"
tar -czf "$OUT/bundle.tgz" -C "$INPUT_DIR" .
sha256sum "$OUT/bundle.tgz" > "$OUT/bundle.tgz.sha256"
