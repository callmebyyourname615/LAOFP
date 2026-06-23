#!/usr/bin/env python3
from __future__ import annotations
import argparse, fnmatch, json, subprocess
from datetime import datetime, timezone
from pathlib import Path
import yaml

def git(root: Path,*args:str)->str:
    try:return subprocess.check_output(["git",*args],cwd=root,text=True,stderr=subprocess.DEVNULL).strip()
    except Exception:return ""

def main()->int:
    p=argparse.ArgumentParser(); p.add_argument('--root',default='.')
    p.add_argument('--contract',required=True); p.add_argument('--output',required=True); p.add_argument('--phase65-baseline')
    a=p.parse_args(); root=Path(a.root).resolve(); cfg=yaml.safe_load(Path(a.contract).read_text())
    head=git(root,'rev-parse','HEAD'); status=git(root,'status','--porcelain=v1')
    phase65_paths=[root/'scripts/phase65',root/'docs/phase65',root/'schemas/phase65',root/'config/phase65']
    phase65_detected=bool(a.phase65_baseline or any(p.exists() for p in phase65_paths) or 'Phase 65' in git(root,'log','-30','--pretty=%B'))
    baseline=a.phase65_baseline or (head if phase65_detected else None)
    changed=[]
    if baseline and baseline != head:
        out=git(root,'diff','--name-only',baseline,'HEAD'); changed=[x for x in out.splitlines() if x]
    elif status:
        tracked=git(root,'diff','--name-only').splitlines() + git(root,'diff','--cached','--name-only').splitlines()
        untracked=git(root,'ls-files','--others','--exclude-standard').splitlines()
        changed=sorted(set(x for x in tracked+untracked if x))
    allow=cfg['allowedPhase66Paths']; protected=cfg['protectedPaths']
    violations=[]
    for path in changed:
        in_allow=any(fnmatch.fnmatch(path,pat) for pat in allow)
        in_protected=any(fnmatch.fnmatch(path,pat) for pat in protected)
        if in_protected or (path.startswith(('scripts/phase66/','docs/phase66/','schemas/phase66/','config/phase66/','sql/phase66/','AGENT/')) and not in_allow):
            violations.append(path)
    import re
    def migration_key(path):
        m=re.match(r'V([0-9]+(?:\.[0-9]+)?)__',path.name)
        return float(m.group(1)) if m else -1
    migrations=sorted((root/'src/main/resources/db/migration').glob('V*__*.sql'), key=migration_key)
    doc={"schemaVersion":1,"generatedAt":datetime.now(timezone.utc).isoformat().replace('+00:00','Z'),
         "head":head,"phase65Baseline":baseline,"phase65Detected":phase65_detected,"dirty":bool(status),
         "latestMigration":migrations[-1].name if migrations else None,"migrationCount":len(migrations),
         "changedFiles":changed,"violations":sorted(set(violations))}
    out=Path(a.output); out.parent.mkdir(parents=True,exist_ok=True); out.write_text(json.dumps(doc,indent=2,sort_keys=True)+'\n')
    print(out)
    return 2 if violations else 0
if __name__=='__main__': raise SystemExit(main())
