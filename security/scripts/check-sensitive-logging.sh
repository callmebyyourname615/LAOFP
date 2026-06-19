#!/usr/bin/env bash
set -euo pipefail
pattern='log\.(trace|debug|info|warn|error)\([^;]*(signingSecret|signing_secret|password|secretCiphertext|previousSecretCiphertext|authorizationHeader|apiKeyValue)'
matches=$(grep -RInE "$pattern" src/main/java --include='*.java' || true)
if [[ -n "$matches" ]]; then
  printf '%s\n' "$matches" >&2
  echo 'Potential sensitive value passed to a logger. Review required.' >&2
  exit 1
fi
if grep -RInE '(BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY|AKIA[0-9A-Z]{16})' . \
  --exclude-dir=.git --exclude-dir=target --exclude='*.patch' --exclude='*.zip'; then
  echo 'Private key or cloud access-key pattern found.' >&2
  exit 1
fi
echo 'Sensitive logging static check passed.'
