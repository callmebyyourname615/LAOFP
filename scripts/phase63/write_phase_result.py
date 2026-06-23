#!/usr/bin/env python3
from __future__ import annotations
import argparse, hashlib, json
from pathlib import Path


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument('--phase', required=True)
    parser.add_argument('--name', required=True)
    parser.add_argument('--status', choices=('PASS', 'FAIL', 'PREPARED', 'BLOCKED'), required=True)
    parser.add_argument('--exit-code', type=int, required=True)
    parser.add_argument('--started-at', required=True)
    parser.add_argument('--finished-at', required=True)
    parser.add_argument('--message', default='')
    parser.add_argument('--root', type=Path, required=True)
    parser.add_argument('--phase-dir', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    artifacts = []
    for path in sorted(args.phase_dir.rglob('*')):
        if path.is_file() and path.resolve() != args.output.resolve():
            try:
                artifact_path = path.relative_to(args.root).as_posix()
            except ValueError:
                artifact_path = f"external-evidence/{path.relative_to(args.phase_dir).as_posix()}"
            artifacts.append({
                'path': artifact_path,
                'bytes': path.stat().st_size,
                'sha256': sha256(path),
            })
    try:
        import subprocess
        commit = subprocess.check_output(['git', '-C', str(args.root), 'rev-parse', 'HEAD'], text=True, timeout=5).strip()
    except Exception:
        commit = 'unavailable'
    document = {
        'schemaVersion': 1,
        'phase': args.phase,
        'name': args.name,
        'status': args.status,
        'exitCode': args.exit_code,
        'startedAt': args.started_at,
        'finishedAt': args.finished_at,
        'message': args.message,
        'gitCommit': commit,
        'artifacts': artifacts,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(document, indent=2, sort_keys=True) + '\n', encoding='utf-8')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
