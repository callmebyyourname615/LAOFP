#!/usr/bin/env python3
from __future__ import annotations
import argparse, pathlib, subprocess, sys

def main() -> int:
    parser=argparse.ArgumentParser(); parser.add_argument('--repository',default='.'); parser.add_argument('--output',default='build/phase67-static.json'); args=parser.parse_args()
    tool=pathlib.Path(args.repository)/'scripts/phase67/phase67_control.py'
    completed=subprocess.run([sys.executable,str(tool),'static','--repository',args.repository,'--output',args.output],check=False)
    return completed.returncode
if __name__=='__main__': raise SystemExit(main())
