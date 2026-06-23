#!/usr/bin/env python3
from __future__ import annotations
import argparse, json
from pathlib import Path

REQUIRED_PASS = {'61A', '61B', '61D', '61E'}
REQUIRED_PREPARED: set[str] = set()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument('--run-dir', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    results = {}
    errors = []
    for path in sorted(args.run_dir.glob('61*/result.json')):
        data = json.loads(path.read_text(encoding='utf-8'))
        results[data.get('phase')] = data
    expected = REQUIRED_PASS | REQUIRED_PREPARED
    missing = sorted(expected - set(results))
    if missing:
        errors.append(f'missing Phase 61 results: {missing}')
    for phase in sorted(REQUIRED_PASS):
        if (results.get(phase) or {}).get('status') != 'PASS':
            errors.append(f'{phase} must be PASS in repository mode')
    commits = {item.get('gitCommit') for item in results.values() if item.get('gitCommit')}
    if len(commits) > 1:
        errors.append('Phase 61 results were produced from different commits')
    document = {'schemaVersion': 1, 'passed': not errors, 'errors': errors,
                'statuses': {key: value.get('status') for key, value in sorted(results.items())}}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(document, indent=2, sort_keys=True) + '\n', encoding='utf-8')
    print(f"Phase 63B Phase 61 repository verification: {'PASS' if not errors else 'FAIL'}")
    for error in errors:
        print('  ERROR:', error)
    return 0 if not errors else 1


if __name__ == '__main__':
    raise SystemExit(main())
