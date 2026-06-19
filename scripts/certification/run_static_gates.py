#!/usr/bin/env python3
"""Run production-readiness static verifiers sequentially with bounded timeouts.

Sequential execution avoids the resource contention and nondeterministic timeouts
caused by the legacy concurrent runner while keeping each verifier isolated in its
own subprocess.
"""
from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
COMMANDS = [
    [sys.executable, "scripts/verify_phase8_static.py"],
    [sys.executable, "scripts/verify_phases_54a_54j.py"],
    [sys.executable, "scripts/monitoring/verify_alert_runbooks.py"],
    [sys.executable, "scripts/validate_production_environment.py", "--verify-k8s"],
    [sys.executable, "scripts/verify_phases_53c_53j.py"],
    [sys.executable, "scripts/verify_phase53b_schema_alignment.py"],
    [sys.executable, "scripts/verify_phase1_static.py"],
    [sys.executable, "scripts/verify_phases_02_04_static.py"],
    [sys.executable, "scripts/verify_phases_05_07_static.py"],
    [sys.executable, "scripts/verify_phases_13_22_static.py"],
    [sys.executable, "scripts/verify_phases_23_32_static.py"],
    [sys.executable, "scripts/verify_phases_33_42_static.py"],
    [sys.executable, "scripts/verify_phases_43_52_static.py"],
]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--timeout-seconds", type=int, default=300)
    args = parser.parse_args()
    failures: list[tuple[str, int]] = []
    for command in COMMANDS:
        label = " ".join(command[1:])
        print(f"\n==> {label}", flush=True)
        try:
            result = subprocess.run(command, cwd=ROOT, check=False, timeout=args.timeout_seconds)
            code = result.returncode
        except subprocess.TimeoutExpired:
            print(f"verifier exceeded {args.timeout_seconds} seconds", file=sys.stderr, flush=True)
            code = 124
        if code:
            failures.append((label, code))
    if failures:
        print("\nProduction readiness static gates: FAIL", file=sys.stderr)
        for label, code in failures:
            print(f"  - {label}: exit {code}", file=sys.stderr)
        return 1
    print("\nProduction readiness static gates: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
