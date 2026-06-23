#!/usr/bin/env python3
import argparse
import hashlib
import pathlib
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument("--bundle-dir", required=True)
args = parser.parse_args()
root = pathlib.Path(args.bundle_dir)
for line in (root / "SHA256SUMS").read_text(encoding="utf-8").splitlines():
    expected, rel = line.split("  ", 1)
    path = root / rel
    actual = hashlib.sha256(path.read_bytes()).hexdigest()
    if actual != expected:
        raise SystemExit(f"checksum mismatch: {rel}")
subprocess.run(["openssl", "dgst", "-sha256", "-verify", str(root / "resilience-signing-public.pem"), "-signature", str(root / "resilience-manifest.sig"), str(root / "resilience-manifest.json")], check=True)
print("Phase 73 bundle verified")
