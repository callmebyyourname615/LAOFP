#!/usr/bin/env python3
import argparse
import datetime as dt
import hashlib
import json
import pathlib
import shutil
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument("--run-dir", required=True)
parser.add_argument("--approval", required=True)
parser.add_argument("--scenario-summary", required=True)
parser.add_argument("--bundle-dir", required=True)
parser.add_argument("--signing-key", required=True)
args = parser.parse_args()
run_dir = pathlib.Path(args.run_dir).resolve()
bundle = pathlib.Path(args.bundle_dir).resolve()
if bundle.exists():
    shutil.rmtree(bundle)
bundle.mkdir(parents=True)
results = {}
for phase in [f"73{letter}" for letter in "ABCDEFGHIJ"]:
    path = run_dir / phase / "result.json"
    if phase == "73J":
        continue
    if not path.is_file():
        raise SystemExit(f"missing phase result: {path}")
    doc = json.loads(path.read_text(encoding="utf-8"))
    if doc.get("status") != "PASS":
        raise SystemExit(f"phase is not PASS: {phase}")
    results[phase] = {"status": doc["status"], "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
summary = json.loads(pathlib.Path(args.scenario_summary).read_text(encoding="utf-8"))
if summary.get("status") != "PASS":
    raise SystemExit("scenario summary is not PASS")
approval = json.loads(pathlib.Path(args.approval).read_text(encoding="utf-8"))
try:
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True, stderr=subprocess.DEVNULL).strip()
except Exception:
    commit = "unknown-commit"
manifest = {
    "schemaVersion": 1,
    "runId": run_dir.name,
    "generatedAt": dt.datetime.now(dt.timezone.utc).isoformat().replace("+00:00", "Z"),
    "environment": "uat",
    "gitCommit": commit,
    "status": "PASS",
    "scenarioSummary": {key: summary[key] for key in ["required", "passed", "failed", "passPercent"]},
    "phaseResults": results,
    "artifacts": [],
    "approval": approval,
}
for source, target in [
    (pathlib.Path(args.scenario_summary), bundle / "scenario-summary.json"),
    (pathlib.Path(args.approval), bundle / "chaos-approval.json"),
]:
    shutil.copy2(source, target)
for attestation in sorted(run_dir.rglob("attestation.json")):
    destination = bundle / "attestations" / attestation.parent.name / "attestation.json"
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(attestation, destination)
for phase, _ in results.items():
    destination = bundle / "phase-results" / f"{phase}.json"
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(run_dir / phase / "result.json", destination)
for path in sorted(bundle.rglob("*")):
    if path.is_file():
        manifest["artifacts"].append({
            "path": str(path.relative_to(bundle)),
            "sizeBytes": path.stat().st_size,
            "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        })
manifest_path = bundle / "resilience-manifest.json"
manifest_path.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
subprocess.run(["openssl", "dgst", "-sha256", "-sign", args.signing_key, "-out", str(bundle / "resilience-manifest.sig"), str(manifest_path)], check=True)
subprocess.run(["openssl", "pkey", "-in", args.signing_key, "-pubout", "-out", str(bundle / "resilience-signing-public.pem")], check=True, stdout=subprocess.DEVNULL)
subprocess.run(["openssl", "dgst", "-sha256", "-verify", str(bundle / "resilience-signing-public.pem"), "-signature", str(bundle / "resilience-manifest.sig"), str(manifest_path)], check=True)
checksum_lines = []
for path in sorted(bundle.rglob("*")):
    if path.is_file() and path.name != "SHA256SUMS":
        checksum_lines.append(f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.relative_to(bundle)}")
(bundle / "SHA256SUMS").write_text("\n".join(checksum_lines) + "\n", encoding="utf-8")
print(manifest_path)
