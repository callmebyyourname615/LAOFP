#!/usr/bin/env python3
import argparse,hashlib,json,pathlib,sys

def digest(path):
    h=hashlib.sha256()
    with open(path,'rb') as f:
        for block in iter(lambda:f.read(1024*1024),b''):h.update(block)
    return h.hexdigest()
def main(manifest,root):
    doc=json.loads(pathlib.Path(manifest).read_text(encoding='utf-8')); root=pathlib.Path(root).resolve();fail=[]
    for item in doc.get('artifacts',[]):
        rel=pathlib.PurePosixPath(item['path'])
        if rel.is_absolute() or '..' in rel.parts: fail.append(item['path']+': unsafe path');continue
        path=(root/pathlib.Path(*rel.parts)).resolve()
        if root not in path.parents and path!=root:fail.append(item['path']+': escaped root');continue
        if not path.is_file() or path.is_symlink():fail.append(item['path']+': missing/non-regular');continue
        actual=digest(path)
        if actual!=item['sha256']:fail.append(item['path']+': sha256 mismatch')
        if path.stat().st_size!=item['size_bytes']:fail.append(item['path']+': size mismatch')
    print(json.dumps({'verified':not fail,'failures':fail},sort_keys=True));return 2 if fail else 0
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('manifest');p.add_argument('root');a=p.parse_args();raise SystemExit(main(a.manifest,a.root))
