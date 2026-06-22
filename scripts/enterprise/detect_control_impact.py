#!/usr/bin/env python3
import argparse,fnmatch,pathlib
from lib import load,write
p=argparse.ArgumentParser(); p.add_argument('--changes',required=True); p.add_argument('--rules',required=True); p.add_argument('--output',required=True); p.add_argument('--full-recertification-on-unmapped',action='store_true'); a=p.parse_args()
changes=load(a.changes); paths=changes.get('changedPaths',[])
if not isinstance(paths,list) or not paths or any(not isinstance(x,str) or x.startswith('/') or '..' in pathlib.PurePosixPath(x).parts for x in paths): raise SystemExit('changedPaths must be a non-empty list of safe repository-relative paths')
rules=load(a.rules).get('rules',[]); controls=set(); certs=set(); matched=[]; unmapped=[]
for path in paths:
    hit=[]
    for rule in rules:
        if any(fnmatch.fnmatch(path,pat) for pat in rule.get('pathPatterns',[])):
            hit.append(rule['id']); controls.update(rule.get('controls',[])); certs.update(str(x) for x in rule.get('certifications',[]))
    if hit: matched.append({'path':path,'rules':sorted(hit)})
    else: unmapped.append(path)
full=bool(unmapped and a.full_recertification_on_unmapped)
if full:
    controls.update(str(x) for r in rules for x in r.get('controls',[])); certs.update(str(x) for r in rules for x in r.get('certifications',[]))
out={'schemaVersion':1,'changedPaths':sorted(paths),'matched':matched,'unmappedPaths':sorted(unmapped),'fullRecertificationRequired':full,'impactedControls':sorted(controls),'requiredCertifications':sorted(certs)}
write(a.output,out)
if unmapped and not a.full_recertification_on_unmapped: raise SystemExit('unmapped changes require fail-closed full recertification')
