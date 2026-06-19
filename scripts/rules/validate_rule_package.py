#!/usr/bin/env python3
import json,pathlib,re,sys
SHA=re.compile(r'^[0-9a-f]{64}$')
def main(path):
    doc=json.loads(pathlib.Path(path).read_text(encoding='utf-8'))
    for key in ('schema_version','package_code','domain','version','artifact','artifact_sha256','minimum_test_cases','rollback_version'):
        if doc.get(key) in (None,''):raise SystemExit(f'{key} is required')
    if doc['schema_version']!=1:raise SystemExit('unsupported schema_version')
    if doc['domain'] not in {'FRAUD','AML','SANCTIONS','ROUTING','LIMITS','RISK_SCORING'}:raise SystemExit('invalid domain')
    if not SHA.fullmatch(doc['artifact_sha256']):raise SystemExit('artifact_sha256 must be lowercase SHA-256')
    if pathlib.PurePosixPath(doc['artifact']).is_absolute() or '..' in pathlib.PurePosixPath(doc['artifact']).parts:raise SystemExit('unsafe artifact path')
    if not isinstance(doc['minimum_test_cases'],int) or doc['minimum_test_cases']<10:raise SystemExit('minimum_test_cases must be >=10')
    for key in ('maximum_false_positive_rate','maximum_false_negative_rate'):
        value=float(doc.get(key,0));
        if not 0<=value<=1:raise SystemExit(f'{key} must be 0..1')
    print(f'validated {doc["package_code"]}:{doc["version"]}')
if __name__=='__main__':
    if len(sys.argv)!=2:raise SystemExit('usage: validate_rule_package.py MANIFEST.json')
    main(sys.argv[1])
