#!/usr/bin/env python3
"""Build a portable, content-addressed go-live evidence manifest."""
import argparse
import datetime
import hashlib
import json
import os
import pathlib

parser = argparse.ArgumentParser()
parser.add_argument('--release-digest', required=True)
parser.add_argument('--commit-sha', required=True)
parser.add_argument('--evidence-root', required=True)
parser.add_argument('--output', required=True)
args = parser.parse_args()

root = pathlib.Path(args.evidence_root).resolve()
output_path = pathlib.Path(args.output).resolve()
output_path.parent.mkdir(parents=True, exist_ok=True)

if not root.is_dir():
    raise SystemExit(f'evidence root is not a directory: {root}')
if output_path == root or root in output_path.parents:
    # Exclude a previously generated output from the next manifest build.
    excluded_output = output_path
else:
    excluded_output = None

evidence = []
for path in sorted(root.rglob('*')):
    if not path.is_file() or path == excluded_output:
        continue
    evidence.append({
        'name': path.stem,
        'path': path.relative_to(root).as_posix(),
        'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
        'bytes': path.stat().st_size,
    })

manifest = {
    'schemaVersion': 1,
    'generatedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(),
    'releaseDigest': args.release_digest,
    'commitSha': args.commit_sha,
    # Relative base keeps the bundle portable. Put the manifest beside the
    # evidence directory or pass --evidence-root to the verifier explicitly.
    'evidenceBase': pathlib.Path(os.path.relpath(root, output_path.parent)).as_posix(),
    'evidence': evidence,
    'approvals': [],
    'status': 'DRAFT_NOT_APPROVED',
}
output_path.write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
