#!/usr/bin/env python3
"""Create a digest-bound release candidate manifest from passed Phase 54 results."""
from __future__ import annotations

import argparse
import datetime
import hashlib
import json
import pathlib
import re


def sha(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", required=True)
    parser.add_argument("--reference", required=True)
    parser.add_argument("--git-commit", required=True)
    parser.add_argument("--image-digest", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument(
        "--through",
        choices=["54I", "54J"],
        default="54J",
        help="Create a prerequisite decision through 54I or final candidate through 54J",
    )
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{2,127}", args.reference):
        raise SystemExit("invalid release reference")
    if not re.fullmatch(r"[a-f0-9]{40}", args.git_commit):
        raise SystemExit("invalid git commit")
    if not re.fullmatch(r"sha256:[a-f0-9]{64}", args.image_digest):
        raise SystemExit("invalid image digest")

    root = pathlib.Path(args.root).resolve()
    end_letter = "I" if args.through == "54I" else "J"
    phases = []
    for codepoint in range(ord("A"), ord(end_letter) + 1):
        phase = f"54{chr(codepoint)}"
        result_path = root / "phases" / phase / "result.json"
        if not result_path.is_file():
            raise SystemExit(f"missing phase result: {phase}")
        data = json.loads(result_path.read_text(encoding="utf-8"))
        if data.get("status") != "PASS":
            raise SystemExit(f"phase {phase} is not PASS")
        release = data.get("release", {})
        if (
            release.get("gitCommit") != args.git_commit
            or release.get("imageDigest") != args.image_digest
            or release.get("reference") != args.reference
        ):
            raise SystemExit(f"identity mismatch in {phase}")
        phases.append({"id": phase, "resultSha256": sha(result_path)})

    artifacts = []
    for path in sorted(root.rglob("*")):
        if not path.is_file() or path.is_symlink() or "release-candidate" in path.parts:
            continue
        artifacts.append(
            {
                "path": str(path.relative_to(root)),
                "size": path.stat().st_size,
                "sha256": sha(path),
            }
        )

    decision = "PREREQUISITES_APPROVED" if args.through == "54I" else "CANDIDATE"
    document = {
        "schemaVersion": 1,
        "releaseReference": args.reference,
        "gitCommit": args.git_commit,
        "imageDigest": args.image_digest,
        "createdAt": datetime.datetime.now(datetime.timezone.utc).isoformat().replace("+00:00", "Z"),
        "certifiedThrough": args.through,
        "prerequisitePhases": phases,
        "artifacts": artifacts,
        "decision": decision,
    }
    output = pathlib.Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(document, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
