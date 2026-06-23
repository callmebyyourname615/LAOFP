#!/usr/bin/env python3
from __future__ import annotations
import argparse, hashlib, json, subprocess
from pathlib import Path

ALLOWED = {"PASS", "PREPARED", "BLOCKED", "FAIL"}

def sha256(path: Path) -> str:
    h=hashlib.sha256()
    with path.open('rb') as f:
        for block in iter(lambda:f.read(1024*1024), b''):
            h.update(block)
    return h.hexdigest()

def git_value(root: Path, *args: str) -> str:
    try:
        return subprocess.check_output(["git", *args], cwd=root, text=True, stderr=subprocess.DEVNULL).strip()
    except Exception:
        return "unknown"

def main() -> int:
    p=argparse.ArgumentParser()
    for name in ("phase","name","status","started-at","finished-at","message","mode","root","phase-dir","output"):
        p.add_argument(f"--{name}", required=True)
    p.add_argument("--exit-code", required=True, type=int)
    a=p.parse_args()
    if a.status not in ALLOWED: raise SystemExit(f"invalid status {a.status}")
    root=Path(a.root).resolve(); phase_dir=Path(a.phase_dir).resolve(); output=Path(a.output).resolve()
    artifacts=[]
    for path in sorted(phase_dir.rglob('*')):
        if not path.is_file() or path == output or path.is_symlink(): continue
        artifacts.append({"path":path.relative_to(phase_dir).as_posix(),"size":path.stat().st_size,"sha256":sha256(path)})
    doc={
      "schemaVersion":1,"phase":a.phase,"name":a.name,"mode":a.mode,"status":a.status,
      "exitCode":a.exit_code,"startedAt":a.started_at,"finishedAt":a.finished_at,"message":a.message,
      "repository":{"commit":git_value(root,"rev-parse","HEAD"),"branch":git_value(root,"rev-parse","--abbrev-ref","HEAD")},
      "artifacts":artifacts,
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(doc, indent=2, sort_keys=True)+"\n", encoding="utf-8")
    return 0
if __name__ == '__main__': raise SystemExit(main())
