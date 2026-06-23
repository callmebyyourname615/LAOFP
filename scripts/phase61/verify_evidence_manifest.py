#!/usr/bin/env python3
from __future__ import annotations
import argparse,hashlib,json
from pathlib import Path

def sha(path:Path)->str: return hashlib.sha256(path.read_bytes()).hexdigest()
def main()->int:
 p=argparse.ArgumentParser(); p.add_argument('--manifest',type=Path,required=True); p.add_argument('--schema',type=Path,required=True); a=p.parse_args(); d=json.loads(a.manifest.read_text()); json.loads(a.schema.read_text()); errors=[]; root=a.manifest.parent.resolve()
 expected={f'61{x}' for x in 'ABCDEFGHIJ'}
 if d.get('status')!='PASS': errors.append('manifest status is not PASS')
 if set((d.get('phases') or {}).keys())!=expected: errors.append('manifest must contain exactly 61A-61J')
 seen=set()
 for item in d.get('artifacts') or []:
  rel=item.get('path','')
  if rel in seen: errors.append(f'duplicate artifact {rel}'); continue
  seen.add(rel); path=(root/rel).resolve()
  if root not in path.parents: errors.append(f'artifact escapes run directory: {rel}'); continue
  if not path.is_file(): errors.append(f'missing artifact: {rel}'); continue
  if path.stat().st_size!=item.get('sizeBytes') or sha(path)!=item.get('sha256'): errors.append(f'artifact mismatch: {rel}')
 for phase,item in (d.get('phases') or {}).items():
  path=root/item.get('path','')
  if not path.is_file() or sha(path)!=item.get('sha256'): errors.append(f'{phase} result hash mismatch')
 print(f"Phase 61 evidence verification: {'PASS' if not errors else 'FAIL'} ({len(seen)} artifacts)")
 for e in errors: print('  ERROR:',e)
 return 0 if not errors else 1
if __name__=='__main__': raise SystemExit(main())
