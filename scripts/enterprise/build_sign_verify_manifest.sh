#!/usr/bin/env bash
set -Eeuo pipefail
ENTERPRISE_ROOT_ARG="${1:?enterprise root required}"; PHASE_DIR_ARG="${2:?phase directory required}"; RELEASE_REFERENCE_ARG="${3:?release reference required}"; GIT_COMMIT_ARG="${4:?git commit required}"; IMAGE_DIGEST_ARG="${5:?image digest required}"; PRIVATE_KEY="${6:?private key required}"; PUBLIC_KEY="${7:?public key required}"
MANIFEST="$PHASE_DIR_ARG/evidence-manifest.json"; SIGNATURE="$PHASE_DIR_ARG/evidence-manifest.sig"; CHECKSUM="$PHASE_DIR_ARG/evidence-manifest.sha256"
python3 scripts/enterprise/build_evidence_manifest.py --root "$ENTERPRISE_ROOT_ARG" --include 'phases/57*/result.json' --include 'phases/57J/*.json' --release-reference "$RELEASE_REFERENCE_ARG" --git-commit "$GIT_COMMIT_ARG" --image-digest "$IMAGE_DIGEST_ARG" --output "$MANIFEST"
if command -v sha256sum >/dev/null 2>&1; then sha256sum "$MANIFEST" | sed "s#  .*#  $(basename "$MANIFEST")#" > "$CHECKSUM"; else shasum -a 256 "$MANIFEST" | sed "s#  .*#  $(basename "$MANIFEST")#" > "$CHECKSUM"; fi
scripts/enterprise/sign_and_verify_blob.sh "$MANIFEST" "$SIGNATURE" "$PRIVATE_KEY" "$PUBLIC_KEY"
python3 scripts/enterprise/verify_evidence_manifest.py --manifest "$MANIFEST" --root "$ENTERPRISE_ROOT_ARG" --release-reference "$RELEASE_REFERENCE_ARG" --git-commit "$GIT_COMMIT_ARG" --image-digest "$IMAGE_DIGEST_ARG"
echo 'enterprise evidence manifest built, signed and verified'
