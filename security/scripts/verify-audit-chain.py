#!/usr/bin/env python3
import csv
import hashlib
import json
import sys

rows = list(csv.DictReader(open(sys.argv[1], newline='', encoding='utf-8')))
previous = 'GENESIS'
errors = []

def safe(value):
    return (value or '').replace('\\', '\\\\').replace('"', '\\"').replace('\n', ' ').replace('\r', ' ')

for row in rows:
    if row['previous_hash'] != previous:
        errors.append({'id': row['id'], 'error': 'previous_hash_mismatch'})
    canonical = '|'.join(safe(row[k]) for k in ['event_type', 'reference_type', 'reference_id', 'actor', 'payload'])
    canonical += '|' + row['previous_hash']
    expected = hashlib.sha256(canonical.encode('utf-8')).hexdigest()
    if expected != row['entry_hash']:
        errors.append({'id': row['id'], 'error': 'entry_hash_mismatch'})
    previous = row['entry_hash']

result = {'rows': len(rows), 'errors': errors, 'passed': not errors}
print(json.dumps(result, indent=2))
if errors:
    sys.exit(1)
