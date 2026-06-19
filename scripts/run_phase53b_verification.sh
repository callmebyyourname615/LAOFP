#!/usr/bin/env bash
set -Eeuo pipefail

MODE="${1:-targeted}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

usage() {
  cat <<'EOF'
Usage: scripts/run_phase53b_verification.sh [static|targeted|full]

  static    Run the dependency-free repository contract verifier only.
  targeted  Run static checks plus the Phase 53B mapping and PostgreSQL upgrade tests.
  full      Run targeted checks, then the complete Maven test suite.
EOF
}

case "${MODE}" in
  static|targeted|full) ;;
  -h|--help) usage; exit 0 ;;
  *) echo "Unsupported mode: ${MODE}" >&2; usage >&2; exit 64 ;;
esac

python3 scripts/verify_phase53b_schema_alignment.py

if [[ "${MODE}" == "static" ]]; then
  exit 0
fi

[[ -x ./mvnw ]] || { echo "./mvnw is missing or not executable" >&2; exit 69; }

./mvnw --batch-mode --no-transfer-progress \
  -Dtest=PayloadSha256EntityMappingContractTest,V83PayloadSha256SchemaAlignmentIntegrationTest \
  test

if [[ "${MODE}" == "full" ]]; then
  ./mvnw --batch-mode --no-transfer-progress clean test
fi
