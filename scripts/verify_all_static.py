#!/usr/bin/env python3
from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VERIFIERS = [
    "scripts/verify_phase1_static.py",
    "scripts/verify_phases_02_04_static.py",
    "scripts/verify_phases_05_07_static.py",
    "scripts/verify_phase8_static.py",
    "scripts/verify_phases_13_22_static.py",
    "scripts/verify_phases_23_32_static.py",
    "scripts/verify_phases_33_42_static.py",
    "scripts/verify_phases_43_52_static.py",
    "scripts/verify_phase_ii_01_04_static.py",
    "scripts/verify_phase_ii_05_24_static.py",
    "scripts/verify_smos_user_management_static.py",
    "scripts/verify_critical_dashboards_static.py",
    "scripts/verify_phase60_static.py",
    "scripts/verify_phase61_static.py",
    "scripts/verify_phase62_static.py",
]


def main() -> int:
    parser = argparse.ArgumentParser(description="Run repository static contracts sequentially")
    parser.add_argument("--strict-phase-ii-predecessors", action="store_true")
    args = parser.parse_args()

    for relative in VERIFIERS:
        path = ROOT / relative
        if not path.is_file():
            print(f"FAIL: missing verifier {relative}", file=sys.stderr)
            return 1
        command = [sys.executable, str(path)]
        if relative.endswith("verify_phase_ii_01_04_static.py") and args.strict_phase_ii_predecessors:
            command.append("--strict-predecessors")
        print(f"==> {relative}", flush=True)
        completed = subprocess.run(command, cwd=ROOT, check=False)
        if completed.returncode != 0:
            print(f"FAIL: {relative} exited {completed.returncode}", file=sys.stderr)
            return completed.returncode

    print("OK: all repository static contracts passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
