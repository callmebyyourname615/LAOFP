#!/usr/bin/env python3
from __future__ import annotations
import argparse, json, re
from pathlib import Path
PLACEHOLDER = re.compile(r'(?i)(replace|todo|tbd|change_me|example)')


def valid_text(value: object) -> bool:
    return isinstance(value, str) and bool(value.strip()) and not PLACEHOLDER.search(value)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument('--attestation', type=Path, required=True)
    parser.add_argument('--evidence-dir', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    data = json.loads(args.attestation.read_text(encoding='utf-8'))
    errors = []
    required_logs = ('full-backup.log', 'verify-backup.log', 'pitr-restore.log')
    for name in required_logs:
        path = args.evidence_dir / name
        if not path.is_file() or path.stat().st_size == 0:
            errors.append(f'missing or empty runtime evidence: {name}')
    if data.get('schemaVersion') != 1:
        errors.append('schemaVersion must equal 1')
    for key in ('backupCompleted', 'checksumVerified', 'rowCountsMatched', 'pitrTargetVerified',
                'outboxReplayIdempotent'):
        if data.get(key) is not True:
            errors.append(f'{key} must be true')
    try:
        rpo = float(data.get('rpoMinutes'))
        rto = float(data.get('rtoMinutes'))
        if rpo >= 5: errors.append('rpoMinutes must be less than 5')
        if rto >= 30: errors.append('rtoMinutes must be less than 30')
    except Exception:
        errors.append('rpoMinutes and rtoMinutes must be numeric')
    if int(data.get('transactionLossCount', -1)) != 0:
        errors.append('transactionLossCount must be zero')
    for key in ('backupId', 'pitrTargetTime', 'sreLead', 'databaseLead', 'signedAt', 'changeReference'):
        if not valid_text(data.get(key)):
            errors.append(f'{key} missing or placeholder')
    report = {'schemaVersion': 1, 'passed': not errors, 'errors': errors, 'attestation': data}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + '\n', encoding='utf-8')
    print(f"Phase 63E backup/PITR verification: {'PASS' if not errors else 'FAIL'}")
    for error in errors: print('  ERROR:', error)
    return 0 if not errors else 1


if __name__ == '__main__': raise SystemExit(main())
