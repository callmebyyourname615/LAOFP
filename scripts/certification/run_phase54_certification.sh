#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cert_require_release_identity

run_one() {
  local phase="$1" script="scripts/certification/${phase,,}-"
  case "$phase" in
    54A) script+="build-test.sh";;
    54B) script+="migration.sh";;
    54C) script+="uat-rehearsal.sh";;
    54D) script+="performance.sh";;
    54E) script+="settlement.sh";;
    54F) script+="backup-pitr.sh";;
    54G) script+="dr-recovery.sh";;
    54H) script+="security-supply-chain.sh";;
    54I) script+="observability-alerts.sh";;
    54J) script+="golive-release-candidate.sh";;
    *) cert_die "unknown phase: $phase";;
  esac
  "$script"
}

assemble() {
  local build_rc verify_rc
  set +e
  python3 scripts/certification/build_certification_manifest.py \
    --root "$CERTIFICATION_ROOT" \
    --plan "$CERTIFICATION_PLAN" \
    --environment "$CERTIFICATION_ENVIRONMENT" \
    --reference "$RELEASE_REFERENCE" \
    --git-commit "$RELEASE_GIT_COMMIT" \
    --image-digest "$RELEASE_IMAGE_DIGEST"
  build_rc=$?
  python3 scripts/certification/verify_certification_manifest.py \
    "$CERTIFICATION_ROOT/manifest.json" \
    --expected-commit "$RELEASE_GIT_COMMIT" \
    --expected-digest "$RELEASE_IMAGE_DIGEST" \
    --expected-reference "$RELEASE_REFERENCE"
  verify_rc=$?
  set -e
  (( build_rc == 0 && verify_rc == 0 ))
}

mode="${1:-help}"
case "$mode" in
  54A|54B|54C|54D|54E|54F|54G|54H|54I|54J)
    rc=0
    run_one "$mode" || rc=$?
    assemble || true
    exit "$rc"
    ;;
  preflight)
    # Security certification is deliberately before any cluster deployment.
    for phase in 54A 54B 54H; do
      run_one "$phase"
    done
    assemble || true
    ;;
  full)
    # Fail closed: do not continue into traffic or destructive drills after a
    # failed prerequisite. The plan remains alphabetically reported even though
    # security certification runs before UAT deployment.
    for phase in 54A 54B 54H 54C 54D 54E 54F 54G 54I 54J; do
      run_one "$phase"
      assemble || true
    done
    assemble
    ;;
  assemble)
    assemble
    ;;
  *)
    echo "Usage: $0 {54A|54B|54C|54D|54E|54F|54G|54H|54I|54J|preflight|full|assemble}" >&2
    exit 64
    ;;
esac
