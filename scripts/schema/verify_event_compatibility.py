#!/usr/bin/env python3
"""Conservative JSON-schema backward compatibility check for event contracts."""
from __future__ import annotations
import argparse
import json
from pathlib import Path


def normalized_type(value):
    if isinstance(value, list):
        return tuple(sorted(value))
    return value


def compare(baseline: dict, candidate: dict) -> list[str]:
    errors: list[str] = []
    old_required = set(baseline.get('required', []))
    new_required = set(candidate.get('required', []))
    removed_required = sorted(old_required - new_required)
    if removed_required:
        errors.append('required fields removed: ' + ', '.join(removed_required))
    old_props = baseline.get('properties', {})
    new_props = candidate.get('properties', {})
    for name, old in old_props.items():
        if name not in new_props:
            errors.append(f'property removed: {name}')
            continue
        new = new_props[name]
        if normalized_type(old.get('type')) != normalized_type(new.get('type')):
            errors.append(f'type changed for {name}: {old.get("type")} -> {new.get("type")}')
        if 'const' in old and old.get('const') != new.get('const'):
            errors.append(f'const changed for {name}: {old.get("const")} -> {new.get("const")}')
        if isinstance(old.get('maxLength'), int) and isinstance(new.get('maxLength'), int) and new['maxLength'] < old['maxLength']:
            errors.append(f'maxLength narrowed for {name}')
        if isinstance(old.get('minimum'), (int, float)) and isinstance(new.get('minimum'), (int, float)) and new['minimum'] > old['minimum']:
            errors.append(f'minimum narrowed for {name}')
    if baseline.get('additionalProperties') is not False and candidate.get('additionalProperties') is False:
        errors.append('additionalProperties changed from allowed to forbidden')
    return errors


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument('--baseline-dir', default='schemas/events/baseline')
    p.add_argument('--registry', default='schemas/events/schema-registry.json')
    args = p.parse_args()
    registry_path = Path(args.registry).resolve()
    schema_root = registry_path.parent.resolve()
    baseline_root = Path(args.baseline_dir).resolve()
    registry = json.loads(registry_path.read_text())
    failures: list[str] = []
    for item in registry.get('schemas', []):
        candidate_path = Path(item['path']).resolve()
        try:
            candidate_path.relative_to(schema_root)
        except ValueError:
            failures.append(f"schema path escapes registry directory: {item.get('path')}")
            continue
        if candidate_path.is_symlink() or not candidate_path.is_file():
            failures.append(f"schema path is missing or not a regular file: {item.get('path')}")
            continue
        baseline_path = baseline_root / f"{candidate_path.stem}.v{item['version']}.json"
        if not baseline_path.exists():
            failures.append(f'missing baseline: {baseline_path}')
            continue
        baseline = json.loads(baseline_path.read_text())
        candidate = json.loads(candidate_path.read_text())
        for error in compare(baseline, candidate):
            failures.append(f"{item['name']} v{item['version']}: {error}")
    if failures:
        raise SystemExit('\n'.join(failures))
    print(f"verified {len(registry.get('schemas', []))} event schema(s)")
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
