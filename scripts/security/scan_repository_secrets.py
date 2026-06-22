#!/usr/bin/env python3
import argparse,re,sys
from pathlib import Path
p=argparse.ArgumentParser(); p.add_argument('--path',default='.'); p.add_argument('--mode',choices=['manifest','evidence'],required=True); p.add_argument('--manifest'); a=p.parse_args(); root=Path(a.path).resolve()
if not root.exists(): raise SystemExit('scan root does not exist')
if a.mode=='manifest':
 if not a.manifest: raise SystemExit('--manifest is required in manifest mode')
 items=[]
 for raw in Path(a.manifest).read_text(encoding='utf-8').splitlines():
  raw=raw.strip()
  if not raw or raw.startswith('#'): continue
  f=(root/raw).resolve()
  if root not in f.parents and f!=root: raise SystemExit('unsafe manifest path')
  if f.is_file(): items.append(f)
else:
 items=[x for x in root.rglob('*') if x.is_file() and not x.is_symlink()]
patterns=[
 ('private-key',re.compile(r'-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----')),
 ('aws-access-key',re.compile(r'\b(?:AKIA|ASIA)[A-Z0-9]{16}\b')),
 ('github-token',re.compile(r'\bgh[pousr]_[A-Za-z0-9]{30,}\b')),
 ('jwt',re.compile(r'\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b')),
 ('basic-auth-url',re.compile(r'https?://[^\s/:]+:[^\s/@]+@')),
 ('secret-assignment',re.compile(r'(?im)^\s*[A-Za-z0-9_.-]*(?:password|passwd|secret|api[_-]?key|access[_-]?token|private[_-]?key)[A-Za-z0-9_.-]*\s*[:=]\s*["\']?([^\s"\']{8,})')),
]
allow=('${','{{','<','example','placeholder','redacted','change_me','changeme','dummy','fake','test-only','not-a-secret','required','sha256:','secretKeyRef','vault:','env:')
findings=[]
for f in sorted(set(items)):
 try:
  if f.stat().st_size>5_000_000: continue
  data=f.read_bytes()
  if b'\x00' in data[:4096]: continue
  text=data.decode('utf-8')
 except Exception: continue
 for name,rx in patterns:
  for m in rx.finditer(text):
   value=m.group(1) if name=='secret-assignment' and m.lastindex else m.group(0)
   low=value.lower()
   if name=='secret-assignment' and any(x.lower() in low for x in allow): continue
   findings.append((str(f.relative_to(root)),name))
   break
if findings:
 for path,rule in findings: print(f'ERROR: potential secret ({rule}) in {path}',file=sys.stderr)
 raise SystemExit(1)
print(f'secret scan PASS: {len(items)} files ({a.mode})')
