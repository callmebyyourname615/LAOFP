#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cert_require_release_identity
phase_begin 54A "Build & Test Certification"
failed=0
run_check toolchain bash -lc 'java -version && ./mvnw --version && python3 --version' || failed=1
run_check static-gates scripts/certification/run_static_gates.sh || failed=1
run_check repository-hygiene security/scripts/verify-repository-hygiene.sh || failed=1
run_check disabled-test-policy bash -lc '! grep -RInE "@Disabled|@Ignore" src/test/java --include="*.java"' || failed=1
run_check clean-verify ./mvnw --batch-mode --no-transfer-progress clean verify || failed=1
run_check test-and-coverage-summary python3 scripts/certification/summarize_tests.py --output "$PHASE_DIR/test-summary.json" || failed=1
run_check dependency-tree bash -lc './mvnw --batch-mode --no-transfer-progress -DskipTests dependency:tree -DoutputFile="$0"' "$PHASE_DIR/dependency-tree.txt" || failed=1
run_check package-checksums bash -lc '
  out="$1"; mkdir -p "$(dirname "$out")"; : > "$out";
  find target -maxdepth 2 -type f \( -name "*.jar" -o -name "*.xml" \) -print0 | sort -z |
  while IFS= read -r -d "" f; do if command -v sha256sum >/dev/null; then sha256sum "$f"; else shasum -a 256 "$f"; fi; done > "$out";
  test -s "$out"' _ "$PHASE_DIR/artifact-checksums.sha256" || failed=1
copy_if_present target/site/jacoco/jacoco.xml "$PHASE_DIR/jacoco.xml"
if [[ -d target/surefire-reports ]]; then tar -czf "$PHASE_DIR/surefire-reports.tar.gz" -C target surefire-reports; fi
if [[ -d target/failsafe-reports ]]; then tar -czf "$PHASE_DIR/failsafe-reports.tar.gz" -C target failsafe-reports; fi
write_phase_result "$([[ $failed -eq 0 ]] && echo PASS || echo FAIL)"
