#!/usr/bin/env python3
"""Compatibility entrypoint for deterministic production-readiness gates."""
from __future__ import annotations
import os
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
os.chdir(ROOT)
os.execvp("python3", ["python3", "scripts/certification/run_static_gates.py"])
