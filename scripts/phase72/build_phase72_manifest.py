#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import os
from datetime import datetime, timezone
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("--evidence-root", required=True)
parser.add_argument("--attestation")
parser.add_argument("--output", required=True)
args = parser.parse_args()

root = Path(args.evidence_root)
results = [json.loads(path.read_text(encoding="utf-8")) for path in sorted((root / "results").glob("72[A-I].json"))]
by_phase = {result["phase"]: result for result in results}
expected = [f"72{letter}" for letter in "ABCDEFGHI"]
missing = [phase for phase in expected if phase not in by_phase]
statuses = {phase: by_phase[phase]["status"] for phase in expected if phase in by_phase}
mode = "full" if results and all(result.get("mode") == "full" for result in results) else "preflight"

attestation = None
if args.attestation and Path(args.attestation).is_file():
    try:
        attestation = json.loads(Path(args.attestation).read_text(encoding="utf-8"))
    except Exception:
        attestation = None

commit = os.environ.get("PHASE72_GIT_SHA", "unknown")
approvers = (attestation or {}).get("approvers", {})
approved = bool(
    attestation
    and attestation.get("approved") is True
    and attestation.get("approvedAt")
    and all(approvers.get(role) for role in ["engineering", "qa", "sre", "security"])
)
commit_match = bool(attestation and commit != "unknown" and attestation.get("gitCommit") == commit)
synthetic = bool((attestation or {}).get("syntheticEvidence", True))

if missing or any(status in {"FAIL", "BLOCKED"} for status in statuses.values()):
    decision = "BLOCKED"
elif mode != "full":
    decision = "PREPARED"
elif any(status != "PASS" for status in statuses.values()):
    decision = "NO_GO"
elif not approved or not commit_match or synthetic:
    decision = "NO_GO"
else:
    decision = "GO"

output = Path(args.output).resolve()
artifacts: list[dict[str, object]] = []
for path in sorted(root.rglob("*")):
    if not path.is_file() or path.resolve() == output:
        continue
    relative = str(path.relative_to(root))
    if relative in {"SHA256SUMS", "results/72J.json"}:
        continue
    artifacts.append(
        {
            "path": relative,
            "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            "size": path.stat().st_size,
        }
    )

payload = {
    "schemaVersion": 1,
    "phase": "72J",
    "decision": decision,
    "mode": mode,
    "generatedAt": datetime.now(timezone.utc).isoformat(),
    "gitCommit": commit,
    "missingPhases": missing,
    "phaseStatuses": statuses,
    "attestationApproved": approved,
    "attestationCommitMatches": commit_match,
    "syntheticEvidence": synthetic,
    "artifacts": artifacts,
}
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
(root / "SHA256SUMS").write_text(
    "".join(f"{artifact['sha256']}  {artifact['path']}\n" for artifact in artifacts),
    encoding="utf-8",
)
print(decision)
