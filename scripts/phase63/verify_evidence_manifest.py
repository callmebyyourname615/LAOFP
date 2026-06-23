#!/usr/bin/env python3
from __future__ import annotations
import argparse, hashlib, json
from pathlib import Path

def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()

def main()->int:
 p=argparse.ArgumentParser(); p.add_argument('--manifest',type=Path,required=True); p.add_argument('--run-dir',type=Path,required=True); p.add_argument('--mode',choices=('preflight','repo','full'),required=True); a=p.parse_args(); data=json.loads(a.manifest.read_text(encoding='utf-8')); errors=[]
 if data.get('schemaVersion')!=1: errors.append('schemaVersion must equal 1')
 expected='PASS' if a.mode=='full' else 'PREPARED'
 if data.get('gate')!=expected: errors.append(f'gate must equal {expected}')
 seen=set()
 root=a.run_dir.resolve()
 for item in data.get('files') or []:
  rel=item.get('path','')
  if rel in seen: errors.append(f'duplicate artifact path: {rel}'); continue
  seen.add(rel); path=(root/rel).resolve()
  try: path.relative_to(root)
  except ValueError: errors.append(f'path traversal: {rel}'); continue
  if not path.is_file(): errors.append(f'missing artifact: {rel}'); continue
  if path.stat().st_size!=item.get('bytes'): errors.append(f'size mismatch: {rel}')
  if sha(path)!=item.get('sha256'): errors.append(f'hash mismatch: {rel}')
 if not seen: errors.append('manifest contains no artifacts')
 print(f"Phase 63 evidence verification: {'PASS' if not errors else 'FAIL'} ({len(seen)} artifacts)")
 for e in errors: print('  ERROR:',e)
 return 0 if not errors else 1
if __name__=='__main__': raise SystemExit(main())
