#!/usr/bin/env bash
set -Eeuo pipefail
[[ $# -eq 3 ]] || { echo "usage: $0 <blob> <signature-output> <verification-output>" >&2; exit 2; }
blob="$1"; signature="$2"; verification="$3"
: "${ASSURANCE_COSIGN_KEY_REF:?ASSURANCE_COSIGN_KEY_REF is required}"
: "${ASSURANCE_COSIGN_PUBLIC_KEY_REF:?ASSURANCE_COSIGN_PUBLIC_KEY_REF is required}"
command -v cosign >/dev/null 2>&1 || { echo "cosign is required" >&2; exit 1; }
[[ -f "$blob" && ! -L "$blob" ]] || { echo "unsafe or missing blob" >&2; exit 1; }
mkdir -p "$(dirname "$signature")" "$(dirname "$verification")"
COSIGN_PASSWORD="${COSIGN_PASSWORD:-}" cosign sign-blob --yes --key "$ASSURANCE_COSIGN_KEY_REF" --output-signature "$signature" "$blob" >/dev/null
cosign verify-blob --key "$ASSURANCE_COSIGN_PUBLIC_KEY_REF" --signature "$signature" "$blob" >"$verification"
[[ -s "$signature" && -s "$verification" ]] || { echo "signature verification evidence missing" >&2; exit 1; }
