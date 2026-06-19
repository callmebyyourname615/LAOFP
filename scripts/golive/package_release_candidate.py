#!/usr/bin/env python3
"""Create a deterministic gzip-compressed tar archive from a release candidate directory."""
from __future__ import annotations
import argparse, gzip, hashlib, pathlib, tarfile

def sha256(path: pathlib.Path) -> str:
    h=hashlib.sha256()
    with path.open('rb') as f:
        for block in iter(lambda:f.read(1048576),b''): h.update(block)
    return h.hexdigest()

def main()->int:
    ap=argparse.ArgumentParser(); ap.add_argument('--root',required=True); ap.add_argument('--output',required=True); ap.add_argument('--mtime',type=int,required=True); ap.add_argument('--checksum-output',required=True)
    a=ap.parse_args(); root=pathlib.Path(a.root).resolve(); out=pathlib.Path(a.output).resolve()
    if not root.is_dir() or root.is_symlink(): raise SystemExit('root must be a regular directory')
    out.parent.mkdir(parents=True,exist_ok=True)
    with out.open('wb') as raw:
        with gzip.GzipFile(filename='',mode='wb',fileobj=raw,mtime=a.mtime) as gz:
            with tarfile.open(fileobj=gz,mode='w',format=tarfile.PAX_FORMAT) as tar:
                for path in sorted(root.rglob('*'),key=lambda p:p.relative_to(root).as_posix()):
                    if path.is_symlink(): raise SystemExit(f'symlink prohibited: {path}')
                    rel=path.relative_to(root).as_posix(); info=tar.gettarinfo(str(path),arcname=f'release-candidate/{rel}')
                    info.uid=0; info.gid=0; info.uname='root'; info.gname='root'; info.mtime=a.mtime
                    if info.isdir(): info.mode=0o755; tar.addfile(info)
                    elif info.isfile():
                        info.mode=0o644
                        with path.open('rb') as stream: tar.addfile(info,stream)
    checksum=sha256(out); pathlib.Path(a.checksum_output).write_text(f'{checksum}  {out.name}\n')
    print(f'{checksum}  {out}')
    return 0
if __name__=='__main__': raise SystemExit(main())
