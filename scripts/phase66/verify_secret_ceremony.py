#!/usr/bin/env python3
from __future__ import annotations
import argparse,json,re
from pathlib import Path
SENSITIVE=re.compile(r"(?i)(password|secret|token|api[_-]?key)\s*[:=]\s*[\"']?([^\s\"']{8,})")
REQUIRED=['credentialsGenerated','vaultStored','historyPurged','cachesInvalidated','serviceTokensRotated','teamRecloned','secopsSigned','repoCoordinatorSigned']
def main():
 p=argparse.ArgumentParser(); p.add_argument('--root',default='.'); p.add_argument('--attestation'); p.add_argument('--output',required=True); p.add_argument('--contract-only',action='store_true'); a=p.parse_args()
 leaks=[]
 for rel in ['docs/phase66/66H_SECRET_ROTATION_CEREMONY.md','schemas/phase66/secret-rotation-attestation.schema.json']:
  text=(Path(a.root)/rel).read_text(); leaks += [f'{rel}:{m.group(1)}' for m in SENSITIVE.finditer(text) if '${' not in m.group(2)]
 missing=[]
 if not a.contract_only:
  data=json.loads(Path(a.attestation).read_text()); missing=[k for k in REQUIRED if data.get(k) is not True]
  raw=Path(a.attestation).read_text(); leaks += [f'attestation:{m.group(1)}' for m in SENSITIVE.finditer(raw)]
 passed=not leaks and not missing
 Path(a.output).write_text(json.dumps({'schemaVersion':1,'contractOnly':a.contract_only,'leaks':leaks,'missingControls':missing,'passed':passed},indent=2,sort_keys=True)+'\n')
 return 0 if passed else 2
if __name__=='__main__': raise SystemExit(main())
