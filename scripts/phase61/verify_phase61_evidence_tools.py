#!/usr/bin/env python3
from pathlib import Path
import json,sys
ROOT=Path(__file__).resolve().parents[2]; failures=[]
for rel in ('schemas/phase61-result.schema.json','schemas/phase61-evidence-manifest.schema.json','docs/templates/PHASE61_UAT_ENTRY_ATTESTATION.example.json'):
 p=ROOT/rel
 try: json.loads(p.read_text(encoding='utf-8'))
 except Exception as e: failures.append(f'{rel}: {e}')
for rel in ('scripts/phase61/build_evidence_manifest.py','scripts/phase61/verify_evidence_manifest.py'):
 if not (ROOT/rel).is_file(): failures.append(f'missing {rel}')
if failures:
 print('Phase 61J evidence tools: FAIL'); [print('  ERROR:',x) for x in failures]; sys.exit(1)
print('Phase 61J evidence tools: PASS')
