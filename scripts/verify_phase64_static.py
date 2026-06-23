#!/usr/bin/env python3
from __future__ import annotations

import json
import os
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent
PHASES = [f"64{letter}" for letter in "ABCDEFGHIJ"]
EXPECTED = [
    "AGENT/PHASE_64A_64J_IMPLEMENTATION_CHECKLIST.md",
    "config/phase64-entry-gate.yaml",
    "schemas/phase64-entry-decision.schema.json",
    "schemas/phase64-result.schema.json",
    "scripts/phase64/common.sh",
    "scripts/phase64/run_phase64.sh",
    "scripts/phase64/64A-uat-environment-readiness.sh",
    "scripts/phase64/64B-phase61-evidence-acquisition.sh",
    "scripts/phase64/64C-runtime-evidence-acquisition.sh",
    "scripts/phase64/64D-test-evidence-certification.sh",
    "scripts/phase64/64E-performance-capacity-evidence.sh",
    "scripts/phase64/64F-backup-pitr-evidence.sh",
    "scripts/phase64/64G-dr-recovery-evidence.sh",
    "scripts/phase64/64H-alert-firing-certification.sh",
    "scripts/phase64/64I-phase54-entry-gate.sh",
    "scripts/phase64/64J-signed-uat-handoff-bundle.sh",
    "scripts/phase64/write_phase_result.py",
    "scripts/phase64/verify_release_identity.py",
    "scripts/phase64/extract_runtime_controls.py",
    "scripts/phase64/validate_performance_evidence.py",
    "scripts/phase64/validate_backup_attestation.py",
    "scripts/phase64/validate_dr_attestation.py",
    "scripts/phase64/validate_alert_attestation.py",
    "scripts/phase64/generate_alert_attestation_template.py",
    "scripts/phase64/evaluate_entry_gate.py",
    "scripts/phase64/build_signed_bundle.py",
    "docs/phase64/PHASE64_UAT_EVIDENCE_GUIDE.md",
    "docs/templates/PHASE64_BACKUP_PITR_ATTESTATION.example.json",
    "docs/templates/PHASE64_DR_ATTESTATION.example.json",
    "docs/templates/PHASE64_ALERT_ATTESTATION.example.json",
    "docs/templates/PHASE64_HANDOFF_APPROVAL.example.json",
    ".github/workflows/phase64-uat-evidence.yml",
    "scripts/execute-and-verify/08-phase64-preflight.sh",
]


def main() -> int:
    errors: list[str] = []
    for relative in EXPECTED:
        if not (ROOT / relative).is_file():
            errors.append(f"missing file: {relative}")
    config_path = ROOT / "config/phase64-entry-gate.yaml"
    if config_path.is_file():
        config = yaml.safe_load(config_path.read_text(encoding="utf-8"))
        if config.get("schemaVersion") != 1:
            errors.append("phase64 config schemaVersion must be 1")
        if config.get("allowedEnvironments") != ["uat"]:
            errors.append("Phase 64 full mode must be UAT-only")
        if config.get("requiredPhases") != PHASES[:8]:
            errors.append("Phase 54 gate must require exactly 64A-64H")
        expected_scenarios = {"smoke", "sustained-2k-tps", "sustained-10k-tps", "burst-20k-tps", "soak-8h"}
        if set(config.get("performance", {}).get("scenarios", {})) != expected_scenarios:
            errors.append("performance scenarios do not match the Phase 64 contract")
        if len(config.get("dr", {}).get("requiredScenarios", [])) != 6:
            errors.append("Phase 64 must require six DR scenarios")
    for schema in ("schemas/phase64-entry-decision.schema.json", "schemas/phase64-result.schema.json"):
        path = ROOT / schema
        if path.is_file():
            json.loads(path.read_text(encoding="utf-8"))
    for template in (ROOT / "docs/templates").glob("PHASE64_*.example.json"):
        json.loads(template.read_text(encoding="utf-8"))
    script_dir = ROOT / "scripts/phase64"
    for path in sorted(script_dir.glob("*.sh")):
        text = path.read_text(encoding="utf-8")
        if not text.startswith("#!/usr/bin/env bash\nset -Eeuo pipefail"):
            errors.append(f"unsafe shell prologue: {path.relative_to(ROOT)}")
        if not os.access(path, os.X_OK):
            errors.append(f"script is not executable: {path.relative_to(ROOT)}")
    orchestrator = (script_dir / "run_phase64.sh").read_text(encoding="utf-8") if (script_dir / "run_phase64.sh").is_file() else ""
    for phase in PHASES:
        if phase not in orchestrator:
            errors.append(f"orchestrator does not reference {phase}")
    workflow = ROOT / ".github/workflows/phase64-uat-evidence.yml"
    if workflow.is_file():
        data = yaml.safe_load(workflow.read_text(encoding="utf-8"))
        if not isinstance(data, dict) or "jobs" not in data:
            errors.append("invalid Phase 64 workflow")
    if errors:
        print(f"Phase 64 static verification: FAIL ({len(errors)} issue(s))")
        for error in errors:
            print(f"  - {error}")
        return 1
    print(f"Phase 64 static verification: PASS ({len(EXPECTED)} required files)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
