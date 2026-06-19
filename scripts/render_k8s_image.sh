#!/usr/bin/env bash
set -Eeuo pipefail

SOURCE_FILE="${1:-}"
OUTPUT_FILE="${2:-}"
IMAGE_REPOSITORY="${3:-}"
IMAGE_DIGEST="${4:-}"

if [[ -z "${SOURCE_FILE}" || -z "${OUTPUT_FILE}" || -z "${IMAGE_REPOSITORY}" || -z "${IMAGE_DIGEST}" ]]; then
  echo "Usage: $0 <source-yaml> <output-yaml> <image-repository> <sha256:digest>" >&2
  exit 64
fi

if [[ ! "${IMAGE_REPOSITORY}" =~ ^ghcr\.io/[a-z0-9._/-]+/switching-(api|backup)$ ]]; then
  echo "Invalid GHCR image repository: ${IMAGE_REPOSITORY}" >&2
  exit 65
fi
if [[ ! "${IMAGE_DIGEST}" =~ ^sha256:[a-f0-9]{64}$ ]]; then
  echo "Invalid image digest: ${IMAGE_DIGEST}" >&2
  exit 65
fi

python3 - "${SOURCE_FILE}" "${OUTPUT_FILE}" "${IMAGE_REPOSITORY}" "${IMAGE_DIGEST}" <<'PY'
from pathlib import Path
import sys
source, output, repository, digest = sys.argv[1:]
text = Path(source).read_text()
image_name = repository.rsplit('/', 1)[-1]
placeholders = {
    "switching-api": "ghcr.io/REPLACE_WITH_GITHUB_REPOSITORY/switching-api@sha256:REPLACE_WITH_IMAGE_DIGEST",
    "switching-backup": "ghcr.io/REPLACE_WITH_GITHUB_REPOSITORY/switching-backup@sha256:REPLACE_WITH_BACKUP_IMAGE_DIGEST",
}
placeholder = placeholders.get(image_name)
if not placeholder or placeholder not in text:
    raise SystemExit(f"image placeholder for {image_name} not found in {source}")
Path(output).parent.mkdir(parents=True, exist_ok=True)
Path(output).write_text(text.replace(placeholder, f"{repository}@{digest}"))
PY
