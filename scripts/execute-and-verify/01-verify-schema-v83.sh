#!/usr/bin/env bash
# Action #1 — Verify V83 schema alignment.
#   1. ddl-auto must be 'validate' in application.yml
#   2. V83 migration file exists and is contiguous (V1..V83)
#   3. Run V83-related integration tests against a real DB
set -euo pipefail
cd "$(dirname "$0")/../.."

echo "[1/3] Checking ddl-auto setting…"
grep -E "^\s*ddl-auto:\s*validate" src/main/resources/application.yml \
  || { echo "FAIL: ddl-auto is not 'validate' in application.yml"; exit 1; }
echo "  OK"

echo "[2/3] Checking V83 migration presence + contiguity…"
test -f src/main/resources/db/migration/V83__align_payload_sha256_to_varchar.sql \
  || { echo "FAIL: V83 migration missing"; exit 1; }
missing=$(python3 - <<'PY'
import os, re
files = os.listdir("src/main/resources/db/migration")
nums = sorted({int(m.group(1)) for f in files if (m := re.match(r"V(\d+)__", f))})
expected = list(range(1, max(nums)+1))
missing = [n for n in expected if n not in nums]
print(",".join(map(str, missing)))
PY
)
if [ -n "$missing" ]; then
  echo "FAIL: missing migrations: $missing"; exit 1
fi
echo "  OK (V1..V83 contiguous)"

echo "[3/3] Running V83 integration tests (requires Docker for Testcontainers)…"
if ! command -v docker >/dev/null 2>&1 || ! docker info >/dev/null 2>&1; then
  echo "  SKIP: docker daemon not available"
  echo "  ⚠️  Run later: ./mvnw test -Dtest='*V83*,*PayloadSha256*'"
  exit 0
fi
./mvnw -q test -Dtest='V83PayloadSha256SchemaAlignmentIntegrationTest,V83CleanInstallCertificationIntegrationTest,PayloadSha256EntityMappingContractTest,MigrationRuntimeIsolationContractTest' \
  -DfailIfNoTests=false
echo "  OK"
