#!/usr/bin/env python3
import datetime
import hashlib
import json
import os
import pathlib
import sys

root = pathlib.Path(sys.argv[1])
files = []
for path in sorted(root.rglob('*')):
    if path.is_file() and path.name != 'evidence.json':
        files.append({
            'path': str(path.relative_to(root)),
            'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
            'bytes': path.stat().st_size,
        })
output = {
    'generatedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(),
    'environment': os.environ.get('DR_ENVIRONMENT'),
    'files': files,
    'status': 'AWAITING_HUMAN_SIGN_OFF',
}
(root / 'evidence.json').write_text(json.dumps(output, indent=2) + '\n', encoding='utf-8')
