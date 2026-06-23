#!/usr/bin/env python3
import argparse, json, re
from pathlib import Path
root=Path(__file__).resolve().parents[2]
errors=[]
helper=root/'src/main/java/com/example/switching/jdbc/JdbcTemporalBinder.java'
service=root/'src/main/java/com/example/switching/crossborder/service/CrossBorderTransferService.java'
test=root/'src/test/java/com/example/switching/jdbc/JdbcTemporalBinderTest.java'
for p in (helper,service,test):
    if not p.is_file(): errors.append(f'missing {p.relative_to(root)}')
if helper.is_file():
    text=helper.read_text(errors='replace')
    for token in ('Types.TIMESTAMP_WITH_TIMEZONE','Types.TIMESTAMP','Types.DATE','OffsetDateTime.ofInstant'):
        if token not in text: errors.append(f'JdbcTemporalBinder missing {token}')
if service.is_file():
    text=service.read_text(errors='replace')
    for token in ('JdbcTemporalBinder.bindTimestamp','JdbcTemporalBinder.bindDate'):
        if token not in text: errors.append(f'CrossBorderTransferService missing {token}')
# Detect the exact unsafe pattern: setObject(index, an Instant variable) without a SQL type.
unsafe=[]
for base in (root/'src/main/java', root/'src/test/java'):
    for p in base.rglob('*.java'):
        txt=p.read_text(errors='replace')
        for line_no,line in enumerate(txt.splitlines(),1):
            if 'setObject(' in line and re.search(r'\binstant\b|Instant\.now\(',line,re.I):
                if 'Types.' not in line:
                    unsafe.append(f'{p.relative_to(root)}:{line_no}:{line.strip()}')
if unsafe: errors.extend('untyped Instant binding '+x for x in unsafe)
result={'schemaVersion':1,'certified':not errors,'errors':errors,'unsafeBindings':unsafe}
ap=argparse.ArgumentParser(); ap.add_argument('--output'); args=ap.parse_args()
if args.output: Path(args.output).write_text(json.dumps(result,indent=2,sort_keys=True)+'\n')
print('Phase 71A timestamp binding PASS' if not errors else '\n'.join('FAIL: '+x for x in errors))
raise SystemExit(0 if not errors else 1)
