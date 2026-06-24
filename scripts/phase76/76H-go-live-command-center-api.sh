#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(cd "$(dirname "$0")/../.." && pwd)"
source "$(dirname "$0")/common.sh"
files=(src/main/java/com/example/switching/readiness/controller/ReadinessController.java src/main/java/com/example/switching/readiness/service/ReadinessDecisionService.java)
for f in "${files[@]}"; do [[ -s "$ROOT/$f" ]] || { write_result 76H FAIL "Missing API source $f"; exit 1; }; done
grep -q '@PreAuthorize' "$ROOT/${files[0]}"
grep -q 'ConditionalOnProperty' "$ROOT/${files[0]}"
write_result 76H PASS "Feature-gated RBAC command-center API present"
