#!/usr/bin/env python3
from __future__ import annotations
import argparse, json
from datetime import datetime, timezone
from pathlib import Path
import yaml


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument('--contract', type=Path, required=True)
    parser.add_argument('--probe', type=Path, required=True)
    parser.add_argument('--platform', choices=('kubernetes', 'docker'), required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    contract = yaml.safe_load(args.contract.read_text(encoding='utf-8')) or {}
    probe = json.loads(args.probe.read_text(encoding='utf-8'))
    gaps = list(probe.get('errors') or [])
    requirements = contract.get('requirements') or {}
    if args.platform == 'docker':
        for key in ('podDisruptionBudget', 'horizontalPodAutoscaler', 'networkPolicy'):
            if requirements.get(key):
                gaps.append(f'{key} cannot be proven from Docker inventory; document the UAT deviation')
    doc = {
        'schemaVersion': 1,
        'generatedAt': datetime.now(timezone.utc).isoformat().replace('+00:00', 'Z'),
        'platform': args.platform,
        'probePassed': probe.get('passed') is True,
        'requirements': requirements,
        'gaps': gaps,
        'passed': probe.get('passed') is True and not gaps,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(doc, indent=2, sort_keys=True) + '\n', encoding='utf-8')
    print(f"Phase 63A gap report: {'PASS' if doc['passed'] else 'FAIL'}")
    for gap in gaps:
        print('  GAP:', gap)
    return 0 if doc['passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
