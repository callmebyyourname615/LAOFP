#!/usr/bin/env python3
from __future__ import annotations
import argparse, json, re
from pathlib import Path
PLACEHOLDER = re.compile(r'(?i)(replace|todo|tbd|change_me|example)')
REQUIRED = {'podKill', 'kafkaBrokerFailure', 'objectStorageFailure', 'networkPartition',
            'externalApiTimeout', 'deploymentRollback', 'postgresFailover', 'postgresFailback'}


def text(value: object) -> bool:
    return isinstance(value, str) and bool(value.strip()) and not PLACEHOLDER.search(value)


def main() -> int:
    p = argparse.ArgumentParser(); p.add_argument('--attestation', type=Path, required=True)
    p.add_argument('--evidence-dir', type=Path, required=True); p.add_argument('--output', type=Path, required=True)
    a = p.parse_args(); data = json.loads(a.attestation.read_text(encoding='utf-8')); errors = []
    if data.get('schemaVersion') != 1: errors.append('schemaVersion must equal 1')
    scenarios = data.get('scenarios') or {}
    for name in sorted(REQUIRED):
        if scenarios.get(name) != 'PASS': errors.append(f'scenarios.{name} must equal PASS')
    if data.get('outboxReplayIdempotent') is not True: errors.append('outboxReplayIdempotent must be true')
    if data.get('failbackVerified') is not True: errors.append('failbackVerified must be true')
    if int(data.get('transactionLossCount', -1)) != 0: errors.append('transactionLossCount must be zero')
    try:
        if float(data.get('rpoMinutes')) >= 5: errors.append('rpoMinutes must be less than 5')
        if float(data.get('rtoMinutes')) >= 30: errors.append('rtoMinutes must be less than 30')
    except Exception: errors.append('rpoMinutes and rtoMinutes must be numeric')
    for key in ('sreLead', 'applicationLead', 'databaseLead', 'signedAt', 'changeReference'):
        if not text(data.get(key)): errors.append(f'{key} missing or placeholder')
    expected_files = [
        'dr/reconciliation.json', 'dr/replay-integrity.json', 'dr/evidence.json',
        'platform/postgres-failover.log', 'platform/postgres-failback.log',
    ]
    for name in expected_files:
        path = a.evidence_dir / name
        if not path.is_file() or path.stat().st_size == 0: errors.append(f'missing or empty runtime evidence: {name}')
    report = {'schemaVersion': 1, 'passed': not errors, 'errors': errors, 'attestation': data}
    a.output.parent.mkdir(parents=True, exist_ok=True); a.output.write_text(json.dumps(report, indent=2, sort_keys=True)+'\n', encoding='utf-8')
    print(f"Phase 63F DR verification: {'PASS' if not errors else 'FAIL'}")
    for error in errors: print('  ERROR:', error)
    return 0 if not errors else 1


if __name__ == '__main__': raise SystemExit(main())
