#!/usr/bin/env python3
"""Verify evidence hashes and independent go-live approvals."""
import argparse
import hashlib
import json
import pathlib
import re
import sys

parser = argparse.ArgumentParser()
parser.add_argument('manifest')
parser.add_argument('--evidence-root', help='Override manifest evidenceBase')
args = parser.parse_args()

manifest_path = pathlib.Path(args.manifest).resolve()
manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
errors = []

digest = str(manifest.get('releaseDigest', ''))
if not re.fullmatch(r'sha256:[0-9a-fA-F]{64}', digest):
    errors.append('invalid release digest')
if not re.fullmatch(r'[0-9a-fA-F]{40}', str(manifest.get('commitSha', ''))):
    errors.append('invalid commit SHA')

root = pathlib.Path(args.evidence_root).resolve() if args.evidence_root else (
    manifest_path.parent / str(manifest.get('evidenceBase', '.'))
).resolve()
if not root.is_dir():
    errors.append(f'evidence root is not a directory: {root}')

required = ['test', 'security', 'dast', 'penetration', 'backup', 'restore', 'performance', 'dr', 'aml', 'operations']
names = ' '.join(str(item.get('name', '')).lower() for item in manifest.get('evidence', []))
for item in required:
    if item not in names:
        errors.append(f'missing evidence category: {item}')

seen_paths = set()
for item in manifest.get('evidence', []):
    rel = pathlib.PurePosixPath(str(item.get('path', '')))
    if rel.is_absolute() or '..' in rel.parts:
        errors.append(f'unsafe evidence path: {rel}')
        continue
    if rel.as_posix() in seen_paths:
        errors.append(f'duplicate evidence path: {rel}')
        continue
    seen_paths.add(rel.as_posix())
    file_path = root.joinpath(*rel.parts).resolve()
    try:
        file_path.relative_to(root)
    except ValueError:
        errors.append(f'evidence path escapes root: {rel}')
        continue
    if not file_path.is_file():
        errors.append(f'missing evidence: {rel}')
        continue
    actual = hashlib.sha256(file_path.read_bytes()).hexdigest()
    if actual != item.get('sha256'):
        errors.append(f'hash mismatch: {rel}')
    if file_path.stat().st_size != item.get('bytes'):
        errors.append(f'size mismatch: {rel}')

approvals = manifest.get('approvals', [])
approved_roles = {
    str(item.get('role', '')).strip().lower()
    for item in approvals
    if str(item.get('decision', '')).upper() == 'APPROVED'
    and str(item.get('approver', '')).strip()
    and str(item.get('approvedAt', '')).strip()
}
required_roles = {'engineering', 'security', 'operations', 'business'}
missing_roles = sorted(required_roles - approved_roles)
if missing_roles:
    errors.append('missing APPROVED sign-off roles: ' + ', '.join(missing_roles))
if str(manifest.get('status', '')).upper() != 'APPROVED':
    errors.append('manifest status must be APPROVED')

print(json.dumps({'passed': not errors, 'errors': errors, 'evidenceRoot': str(root)}, indent=2))
if errors:
    sys.exit(1)
