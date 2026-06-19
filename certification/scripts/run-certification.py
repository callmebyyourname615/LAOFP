#!/usr/bin/env python3
"""Run a participant API certification plan and emit tamper-evident evidence JSON."""
from __future__ import annotations
import argparse
import hashlib
import hmac
import json
import os
import ssl
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import re
from datetime import datetime, timezone
from pathlib import Path


def interpolate(value, variables):
    if isinstance(value, str):
        for key, replacement in variables.items():
            value = value.replace('{{' + key + '}}', str(replacement))
        if '{{' in value:
            raise ValueError(f'unresolved variable in {value}')
        return value
    if isinstance(value, list):
        return [interpolate(v, variables) for v in value]
    if isinstance(value, dict):
        return {k: interpolate(v, variables) for k, v in value.items()}
    return value


def canonical(method, path, query, timestamp, body):
    return '\n'.join([method.upper(), path, query or '', timestamp, body or ''])


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument('--base-url', required=True)
    p.add_argument('--plan', default='certification/spec/participant-certification.json')
    p.add_argument('--output', required=True)
    p.add_argument('--bank-code', required=True)
    p.add_argument('--api-key', default=os.getenv('CERT_API_KEY'))
    p.add_argument('--ca-file')
    p.add_argument('--client-cert')
    p.add_argument('--client-key')
    p.add_argument('--timeout', type=float, default=30.0)
    args = p.parse_args()

    bank_code = args.bank_code.strip().upper()
    if not re.fullmatch(r'[A-Z0-9_-]{2,32}', bank_code):
        raise SystemExit('bank-code must match [A-Z0-9_-]{2,32}')
    parsed_base = urllib.parse.urlsplit(args.base_url)
    if parsed_base.scheme != 'https' or not parsed_base.hostname or parsed_base.username or parsed_base.password:
        raise SystemExit('base-url must be an HTTPS origin without user-info')
    if parsed_base.query or parsed_base.fragment:
        raise SystemExit('base-url must not contain query or fragment')

    plan_bytes = Path(args.plan).read_bytes()
    plan = json.loads(plan_bytes)
    run_id = uuid.uuid4().hex[:20]
    variables = {**plan.get('variables', {}), 'RUN_ID': run_id, 'SOURCE_BANK': bank_code}
    context = ssl.create_default_context(cafile=args.ca_file)
    if args.client_cert:
        context.load_cert_chain(args.client_cert, args.client_key)

    evidence = {
        'schemaVersion': 1,
        'suiteVersion': plan['suiteVersion'],
        'planSha256': hashlib.sha256(plan_bytes).hexdigest(),
        'runId': run_id,
        'bankCode': bank_code,
        'startedAt': datetime.now(timezone.utc).isoformat(),
        'baseUrl': args.base_url,
        'steps': []
    }
    passed = True
    for raw_step in plan['steps']:
        step = interpolate(raw_step, variables)
        method = step['method'].upper()
        path = step['path']
        body = '' if 'body' not in step else json.dumps(step['body'], separators=(',', ':'), sort_keys=True)
        headers = {'Accept': 'application/json'}
        if body:
            headers['Content-Type'] = 'application/json'
        if step.get('authenticated', True):
            if not args.api_key:
                raise SystemExit('CERT_API_KEY or --api-key is required')
            headers['X-API-Key'] = args.api_key
        if step.get('signed'):
            timestamp = str(int(time.time()))
            headers['X-Timestamp'] = timestamp
            signature = hmac.new(args.api_key.encode(), canonical(method, path, '', timestamp, body).encode(), hashlib.sha256).hexdigest()
            headers['X-Request-Signature'] = signature
        request = urllib.request.Request(args.base_url.rstrip('/') + path,
                                         data=body.encode() if body else None,
                                         headers=headers,
                                         method=method)
        started = time.monotonic()
        response_body = b''
        status = 0
        error = None
        try:
            with urllib.request.urlopen(request, context=context, timeout=args.timeout) as response:
                status = response.status
                response_body = response.read(1024 * 1024 + 1)
                if len(response_body) > 1024 * 1024:
                    raise ValueError('response exceeds 1 MiB evidence limit')
        except urllib.error.HTTPError as ex:
            status = ex.code
            response_body = ex.read(1024 * 1024 + 1)
            if len(response_body) > 1024 * 1024:
                error = 'HTTPError response exceeds 1 MiB evidence limit'
                response_body = response_body[:1024 * 1024]
        except Exception as ex:
            error = f'{type(ex).__name__}: {ex}'
        duration_ms = int((time.monotonic() - started) * 1000)
        ok = error is None and status in step['expectedStatus']
        passed = passed and ok
        parsed = None
        if response_body:
            try:
                parsed = json.loads(response_body)
            except json.JSONDecodeError:
                parsed = None
        if ok and step.get('extract'):
            if not isinstance(parsed, dict):
                raise SystemExit(f"step {step['name']} cannot extract from non-object JSON")
            for variable, field in step['extract'].items():
                if field not in parsed:
                    raise SystemExit(f"step {step['name']} missing extraction field {field}")
                variables[variable] = parsed[field]
        evidence['steps'].append({
            'name': step['name'], 'method': method, 'path': path, 'status': status,
            'expectedStatus': step['expectedStatus'], 'durationMs': duration_ms, 'passed': ok,
            'responseSha256': hashlib.sha256(response_body).hexdigest(), 'error': error
        })

    evidence['completedAt'] = datetime.now(timezone.utc).isoformat()
    evidence['result'] = 'PASS' if passed else 'FAIL'
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    canonical_evidence = json.dumps(evidence, indent=2, sort_keys=True) + '\n'
    output.write_text(canonical_evidence)
    output.with_suffix(output.suffix + '.sha256').write_text(hashlib.sha256(canonical_evidence.encode()).hexdigest() + '  ' + output.name + '\n')
    print(json.dumps({'result': evidence['result'], 'steps': len(evidence['steps']), 'output': str(output)}))
    return 0 if passed else 1


if __name__ == '__main__':
    raise SystemExit(main())
