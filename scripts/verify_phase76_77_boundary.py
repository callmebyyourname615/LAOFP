#!/usr/bin/env python3
import argparse, fnmatch, json, subprocess, sys
from pathlib import Path

p=argparse.ArgumentParser()
p.add_argument('--paths-file', help='newline-delimited package path list; defaults to git status paths')
a=p.parse_args()
root=Path(__file__).resolve().parents[1]
if a.paths_file:
    paths=[x.strip() for x in Path(a.paths_file).read_text().splitlines() if x.strip()]
else:
    raw=subprocess.check_output(['git','-C',str(root),'status','--porcelain','-uall'],text=True)
    paths=[line[3:] for line in raw.splitlines() if len(line)>=4]
allowed=[
 'AGENT/PHASE76*','AGENT/PHASE77*','scripts/phase76/**','scripts/phase77/**',
 'scripts/verify_phase76_static.py','scripts/verify_phase77_static.py','scripts/verify_phase76_77_boundary.py','scripts/validate_phase76_77_artifacts.py',
 'scripts/execute-and-verify/15-phase76-operational-evidence.sh','scripts/execute-and-verify/16-phase77-continuous-assurance.sh',
 'docs/phase76/**','docs/phase77/**','config/phase76/**','config/phase77/**','schemas/phase76/**','schemas/phase77/**',
 'evidence/phase76/**','evidence/phase77/**','sql/phase77/**',
 '.github/workflows/phase76-*.yml','.github/workflows/phase77-*.yml',
 'src/main/java/com/example/switching/readiness/**','src/main/java/com/example/switching/continuousassurance/**',
 'src/test/java/com/example/switching/readiness/**','src/test/java/com/example/switching/continuousassurance/**']
forbidden=['scripts/phase74/**','scripts/phase75/**','docs/phase74/**','docs/phase75/**','config/phase74/**','config/phase75/**','schemas/phase74/**','schemas/phase75/**','evidence/phase74/**','evidence/phase75/**','src/main/resources/db/migration/**','pom.xml']
def matches(path, patterns): return any(fnmatch.fnmatch(path, pat) for pat in patterns)
violations=[]
for path in paths:
    if matches(path,forbidden): violations.append({'path':path,'reason':'forbidden'})
    elif not matches(path,allowed): violations.append({'path':path,'reason':'outside allowlist'})
out={'status':'PASS' if not violations else 'FAIL','path_count':len(paths),'violations':violations}
print(json.dumps(out,indent=2)); sys.exit(1 if violations else 0)
