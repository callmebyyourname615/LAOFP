#!/usr/bin/env bash
set -Eeuo pipefail
INPUT="${1:?input required}"; SIGNATURE="${2:?signature output required}"; PRIVATE_KEY="${3:?private key required}"; PUBLIC_KEY="${4:?public key required}"
command -v cosign >/dev/null 2>&1 || { echo 'cosign is required' >&2; exit 69; }
[[ -f "$INPUT" ]] || { echo "input not found: $INPUT" >&2; exit 66; }
cosign sign-blob --yes --key "$PRIVATE_KEY" --output-signature "$SIGNATURE" "$INPUT"
[[ -s "$SIGNATURE" ]] || { echo 'signature was not created' >&2; exit 65; }
cosign verify-blob --key "$PUBLIC_KEY" --signature "$SIGNATURE" "$INPUT"
echo "signed and verified: $(basename "$INPUT")"
