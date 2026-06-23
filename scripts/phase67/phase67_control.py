#!/usr/bin/env python3
"""Phase 67 production cutover control and evidence utilities."""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
import pathlib
import re
import shutil
import subprocess
import tarfile
import tempfile
from typing import Any

try:
    import yaml
except ImportError as exc:  # pragma: no cover
    raise SystemExit("PyYAML is required") from exc

UTC = dt.timezone.utc


def utc_now() -> str:
    return dt.datetime.now(UTC).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def parse_time(value: str) -> dt.datetime:
    parsed = dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None:
        raise ValueError("timestamp must include timezone")
    return parsed.astimezone(UTC)


def load_json(path: pathlib.Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"expected object in {path}")
    return value


def write_json(path: pathlib.Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_file(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def release_from_args(args: argparse.Namespace) -> dict[str, str]:
    return {
        "reference": args.reference,
        "releaseCandidateId": args.rc_id,
        "gitCommit": args.git_commit,
        "applicationImageDigest": args.application_digest,
        "migrationImageDigest": args.migration_digest,
        "environment": getattr(args, "environment", ""),
        "mode": getattr(args, "mode", ""),
    }


def validate_release(release: dict[str, str]) -> list[str]:
    patterns = {
        "reference": r"^[A-Za-z0-9][A-Za-z0-9._-]{2,127}$",
        "releaseCandidateId": r"^switching-[A-Za-z0-9][A-Za-z0-9._-]{2,95}$",
        "gitCommit": r"^[a-f0-9]{40}$",
        "applicationImageDigest": r"^sha256:[a-f0-9]{64}$",
        "migrationImageDigest": r"^sha256:[a-f0-9]{64}$",
    }
    return [f"invalid {key}" for key, pattern in patterns.items() if not re.fullmatch(pattern, str(release.get(key, "")))]


def command_result(args: list[str], cwd: pathlib.Path | None = None) -> tuple[int, str]:
    completed = subprocess.run(args, cwd=cwd, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=False)
    return completed.returncode, completed.stdout.strip()


def cmd_result(args: argparse.Namespace) -> int:
    checks = [json.loads(line) for line in pathlib.Path(args.checks).read_text(encoding="utf-8").splitlines() if line.strip()]
    if not checks:
        status = "FAIL"
    elif any(item.get("status") == "FAIL" for item in checks):
        status = "FAIL"
    elif all(item.get("status") == "PREPARED" for item in checks):
        status = "PREPARED"
    else:
        status = "PASS"
    if args.forced_status == "FAIL":
        status = "FAIL"
    elif args.mode == "preflight" and status != "FAIL":
        status = "PREPARED"
    doc = {
        "schemaVersion": 1,
        "phase": args.phase,
        "name": args.name,
        "status": status,
        "startedAt": args.started_at,
        "endedAt": args.ended_at,
        "release": release_from_args(args),
        "checks": checks,
    }
    write_json(pathlib.Path(args.output), doc)
    return 0 if status in {"PASS", "PREPARED"} else 1


def cmd_prerequisite(args: argparse.Namespace) -> int:
    path = pathlib.Path(args.result)
    errors: list[str] = []
    if not path.is_file() or path.is_symlink():
        errors.append("result file missing or unsafe")
    else:
        data = load_json(path)
        if data.get("phase") != args.phase:
            errors.append("phase mismatch")
        if data.get("status") != "PASS":
            errors.append("prerequisite is not PASS")
        expected = release_from_args(args)
        actual = data.get("release", {})
        for key in ("reference", "releaseCandidateId", "gitCommit", "applicationImageDigest", "migrationImageDigest"):
            if actual.get(key) != expected[key]:
                errors.append(f"release identity mismatch: {key}")
    print(json.dumps({"phase": args.phase, "verified": not errors, "errors": errors}, sort_keys=True))
    return 0 if not errors else 2


def cmd_prerequisites(args: argparse.Namespace) -> int:
    errors: list[str] = []
    verified: list[str] = []
    expected = release_from_args(args)
    for phase in args.phase:
        path = pathlib.Path(args.root) / "phases" / phase / "result.json"
        if not path.is_file() or path.is_symlink():
            errors.append(f"{phase}: result file missing or unsafe")
            continue
        data = load_json(path)
        if data.get("phase") != phase:
            errors.append(f"{phase}: phase mismatch")
        if data.get("status") != "PASS":
            errors.append(f"{phase}: prerequisite is not PASS")
        actual = data.get("release", {})
        for key in ("reference", "releaseCandidateId", "gitCommit", "applicationImageDigest", "migrationImageDigest"):
            if actual.get(key) != expected[key]:
                errors.append(f"{phase}: release identity mismatch: {key}")
        if not any(item.startswith(f"{phase}:") for item in errors):
            verified.append(phase)
    print(json.dumps({"verified": not errors, "phases": verified, "errors": errors}, sort_keys=True))
    return 0 if not errors else 2

def cmd_freeze(args: argparse.Namespace) -> int:
    release = release_from_args(args)
    errors = validate_release(release)
    checks: dict[str, Any] = {"releaseIdentityValid": not errors}
    repo = pathlib.Path(args.repository).resolve()
    if args.mode == "preflight":
        checks.update({"repositoryClean": None, "headMatches": None, "attestationVerified": None})
        output = {"schemaVersion": 1, "generatedAt": utc_now(), "status": "PREPARED", "release": release, "checks": checks, "errors": errors}
        write_json(pathlib.Path(args.output), output)
        return 3 if not errors else 2

    rc, head = command_result(["git", "rev-parse", "HEAD"], repo)
    checks["headMatches"] = rc == 0 and head == args.git_commit
    if not checks["headMatches"]:
        errors.append("repository HEAD does not match release commit")
    rc, status = command_result(["git", "status", "--porcelain", "--untracked-files=no"], repo)
    checks["repositoryClean"] = rc == 0 and status == ""
    if not checks["repositoryClean"]:
        errors.append("repository has tracked modifications")

    attestation_path = pathlib.Path(args.attestation)
    if not attestation_path.is_file() or attestation_path.is_symlink():
        errors.append("change-freeze attestation missing or unsafe")
        checks["attestationVerified"] = False
    else:
        att = load_json(attestation_path)
        approvers = {str(x).strip() for x in att.get("approvedBy", []) if str(x).strip()}
        try:
            age = (dt.datetime.now(UTC) - parse_time(str(att.get("issuedAt", "")))).total_seconds()
        except Exception:
            age = float("inf")
        valid = (
            att.get("status") == "ACTIVE"
            and att.get("releaseReference") == args.reference
            and att.get("gitCommit") == args.git_commit
            and len(approvers) >= args.minimum_approvers
            and 0 <= age <= args.maximum_age_seconds
        )
        checks["attestationVerified"] = valid
        checks["approverCount"] = len(approvers)
        checks["attestationAgeSeconds"] = age if age != float("inf") else None
        if not valid:
            errors.append("change-freeze attestation is invalid or stale")

    output = {"schemaVersion": 1, "generatedAt": utc_now(), "status": "PASS" if not errors else "FAIL", "release": release, "checks": checks, "errors": errors}
    write_json(pathlib.Path(args.output), output)
    return 0 if not errors else 2


def flatten_signals(payload: dict[str, Any]) -> dict[str, Any]:
    signals = payload.get("signals", payload)
    return dict(signals) if isinstance(signals, dict) else {}


def cmd_decision(args: argparse.Namespace) -> int:
    policy = yaml.safe_load(pathlib.Path(args.policy).read_text(encoding="utf-8"))
    payload = load_json(pathlib.Path(args.input))
    signals = flatten_signals(payload)
    rollback_policy = policy["rollback"]
    critical = [name for name in rollback_policy["criticalSignals"] if bool(signals.get(name, False))]
    hold = [name for name in rollback_policy["holdSignals"] if bool(signals.get(name, False))]
    if critical:
        decision = rollback_policy["criticalDecision"]
        reasons = [f"critical:{name}" for name in critical]
    elif hold:
        decision = rollback_policy["holdDecision"]
        reasons = [f"hold:{name}" for name in hold]
    else:
        decision = rollback_policy["healthyDecision"]
        reasons = ["all-evaluated-signals-within-policy"]
    output = {
        "schemaVersion": 1,
        "generatedAt": utc_now(),
        "stage": args.stage,
        "decision": decision,
        "release": release_from_args(args),
        "signals": signals,
        "reasons": reasons,
    }
    write_json(pathlib.Path(args.output), output)
    print(json.dumps({"stage": args.stage, "decision": decision, "reasons": reasons}, sort_keys=True))
    expected = set(args.allowed_decision)
    return 0 if decision in expected else 2


def canonical_event_payload(event: dict[str, Any]) -> bytes:
    unsigned = dict(event)
    unsigned.pop("eventHash", None)
    return json.dumps(unsigned, separators=(",", ":"), sort_keys=True).encode("utf-8")


def cmd_event_append(args: argparse.Namespace) -> int:
    path = pathlib.Path(args.timeline)
    path.parent.mkdir(parents=True, exist_ok=True)
    events = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()] if path.exists() else []
    previous = events[-1]["eventHash"] if events else "GENESIS"
    sequence = len(events) + 1
    event = {
        "schemaVersion": 1,
        "sequence": sequence,
        "eventId": args.event_id or f"evt-{sequence:06d}",
        "recordedAt": args.recorded_at or utc_now(),
        "eventType": args.event_type,
        "actor": args.actor,
        "message": args.message,
        "releaseReference": args.reference,
        "previousHash": previous,
    }
    event["eventHash"] = sha256_bytes(canonical_event_payload(event))
    with path.open("a", encoding="utf-8") as stream:
        stream.write(json.dumps(event, sort_keys=True) + "\n")
    print(json.dumps(event, sort_keys=True))
    return 0


def verify_events(path: pathlib.Path) -> tuple[bool, list[str], int, str]:
    errors: list[str] = []
    previous = "GENESIS"
    count = 0
    last_hash = previous
    if not path.is_file() or path.is_symlink():
        return False, ["timeline missing or unsafe"], 0, previous
    for index, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        if not line.strip():
            continue
        count += 1
        try:
            event = json.loads(line)
        except json.JSONDecodeError:
            errors.append(f"line {index}: invalid JSON")
            continue
        if event.get("sequence") != count:
            errors.append(f"line {index}: sequence mismatch")
        if event.get("previousHash") != previous:
            errors.append(f"line {index}: previous hash mismatch")
        calculated = sha256_bytes(canonical_event_payload(event))
        if event.get("eventHash") != calculated:
            errors.append(f"line {index}: event hash mismatch")
        previous = str(event.get("eventHash", ""))
        last_hash = previous
    if count == 0:
        errors.append("timeline is empty")
    return not errors, errors, count, last_hash


def cmd_event_verify(args: argparse.Namespace) -> int:
    verified, errors, count, last_hash = verify_events(pathlib.Path(args.timeline))
    output = {"schemaVersion": 1, "verified": verified, "eventCount": count, "lastHash": last_hash, "errors": errors}
    if args.output:
        write_json(pathlib.Path(args.output), output)
    print(json.dumps(output, sort_keys=True))
    return 0 if verified else 2


def cmd_hypercare(args: argparse.Namespace) -> int:
    policy = yaml.safe_load(pathlib.Path(args.policy).read_text(encoding="utf-8"))["hypercare"]
    source = load_json(pathlib.Path(args.input))
    checkpoints = source.get("checkpoints", [])
    by_day = {int(item.get("day", -1)): item for item in checkpoints if isinstance(item, dict)}
    required = [int(x) for x in policy["requiredCheckpoints"]]
    errors: list[str] = []
    evaluated: list[dict[str, Any]] = []
    for day in required:
        item = by_day.get(day)
        if item is None:
            errors.append(f"missing checkpoint day {day}")
            continue
        checks = {
            "criticalIncidents": int(item.get("criticalIncidents", 999)) <= int(policy["maximumCriticalIncidents"]),
            "unresolvedHighIncidents": int(item.get("unresolvedHighIncidents", 999)) <= int(policy["maximumUnresolvedHighIncidents"]),
            "balanceMismatchCount": int(item.get("balanceMismatchCount", 999)) <= int(policy["maximumBalanceMismatchCount"]),
            "duplicateBusinessReferenceCount": int(item.get("duplicateBusinessReferenceCount", 999)) <= int(policy["maximumDuplicateBusinessReferenceCount"]),
            "allSignalsPresent": bool(item.get("allSignalsPresent", False)) if policy["requireAllSignalsPresent"] else True,
        }
        if not all(checks.values()):
            errors.append(f"checkpoint day {day} failed exit criteria")
        evaluated.append({"day": day, "status": "PASS" if all(checks.values()) else "FAIL", "checks": checks})
    duration = int(source.get("durationDays", 0))
    if duration < int(policy["minimumDurationDays"]):
        errors.append("minimum hypercare duration not met")
    output = {
        "schemaVersion": 1,
        "generatedAt": utc_now(),
        "status": "PASS" if not errors else "FAIL",
        "durationDays": duration,
        "requiredCheckpoints": required,
        "checkpoints": evaluated,
        "errors": errors,
    }
    write_json(pathlib.Path(args.output), output)
    return 0 if not errors else 2


def safe_artifacts(root: pathlib.Path) -> list[pathlib.Path]:
    artifacts: list[pathlib.Path] = []
    for path in sorted(root.rglob("*")):
        if not path.is_file() or path.is_symlink():
            continue
        if any(part in {"attempts", "bundle"} for part in path.relative_to(root).parts):
            continue
        artifacts.append(path)
    return artifacts


def cmd_bundle(args: argparse.Namespace) -> int:
    root = pathlib.Path(args.root).resolve()
    output_dir = pathlib.Path(args.output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    phase_results: list[dict[str, Any]] = []
    errors: list[str] = []
    for phase in [f"67{letter}" for letter in "ABCDEFGHI"]:
        path = root / "phases" / phase / "result.json"
        if not path.is_file():
            errors.append(f"missing {phase} result")
            continue
        data = load_json(path)
        if data.get("status") != "PASS":
            errors.append(f"{phase} is not PASS")
        phase_results.append({"phase": phase, "status": data.get("status"), "sha256": sha256_file(path)})
    artifacts = safe_artifacts(root)
    manifest_artifacts = [
        {"path": str(path.relative_to(root)), "sha256": sha256_file(path), "sizeBytes": path.stat().st_size}
        for path in artifacts
    ]
    manifest = {
        "schemaVersion": 1,
        "generatedAt": utc_now(),
        "status": "PASS" if not errors else "FAIL",
        "release": release_from_args(args),
        "phaseResults": phase_results,
        "artifacts": manifest_artifacts,
    }
    manifest_path = output_dir / "manifest.json"
    write_json(manifest_path, manifest)
    checksums = output_dir / "checksums.sha256"
    checksums.write_text("".join(f"{item['sha256']}  {item['path']}\n" for item in manifest_artifacts), encoding="utf-8")
    archive = output_dir / "phase67-bau-acceptance.tar.gz"
    with tarfile.open(archive, "w:gz") as tar:
        for path in artifacts:
            tar.add(path, arcname=path.relative_to(root))
        tar.add(manifest_path, arcname="bundle/manifest.json")
        tar.add(checksums, arcname="bundle/checksums.sha256")
    (output_dir / "phase67-bau-acceptance.tar.gz.sha256").write_text(f"{sha256_file(archive)}  {archive.name}\n", encoding="utf-8")
    print(json.dumps({"status": manifest["status"], "artifactCount": len(artifacts), "archive": str(archive), "errors": errors}, sort_keys=True))
    return 0 if not errors else 2


def cmd_verify_bundle(args: argparse.Namespace) -> int:
    archive = pathlib.Path(args.archive)
    expected = pathlib.Path(args.checksum).read_text(encoding="utf-8").split()[0]
    errors: list[str] = []
    if sha256_file(archive) != expected:
        errors.append("archive checksum mismatch")
    with tempfile.TemporaryDirectory(prefix="phase67-verify-") as tmp:
        tmp_path = pathlib.Path(tmp)
        with tarfile.open(archive, "r:gz") as tar:
            for member in tar.getmembers():
                target = (tmp_path / member.name).resolve()
                if not str(target).startswith(str(tmp_path.resolve()) + os.sep):
                    errors.append("unsafe archive member")
                    break
            if not errors:
                tar.extractall(tmp_path, filter="data")
        manifest_path = tmp_path / "bundle" / "manifest.json"
        if not manifest_path.is_file():
            errors.append("manifest missing from archive")
        else:
            manifest = load_json(manifest_path)
            for item in manifest.get("artifacts", []):
                path = tmp_path / item["path"]
                if not path.is_file() or sha256_file(path) != item["sha256"]:
                    errors.append(f"artifact verification failed: {item['path']}")
    output = {"schemaVersion": 1, "verified": not errors, "errors": errors}
    if args.output:
        write_json(pathlib.Path(args.output), output)
    print(json.dumps(output, sort_keys=True))
    return 0 if not errors else 2


def cmd_static(args: argparse.Namespace) -> int:
    root = pathlib.Path(args.repository)
    required = [
        "AGENT/PHASE_67A_67J_IMPLEMENTATION_CHECKLIST.md",
        "config/phase67-production-cutover-policy.yaml",
        "scripts/phase67/common.sh",
        "scripts/phase67/run_phase67.sh",
        "scripts/phase67/phase67_control.py",
        ".github/workflows/phase67-production-cutover.yml",
    ] + [f"scripts/phase67/67{letter}-{name}.sh" for letter, name in zip("ABCDEFGHIJ", [
        "release-identity-freeze-gate", "production-infrastructure-gate", "immutable-rc-provenance",
        "financial-cutover-baseline", "canary-health-gate", "progressive-traffic-gate",
        "rollback-decision-engine", "command-center-recorder", "hypercare-tracker", "bau-acceptance-bundle"
    ])]
    errors = [f"missing {item}" for item in required if not (root / item).is_file()]
    policy = yaml.safe_load((root / "config/phase67-production-cutover-policy.yaml").read_text(encoding="utf-8"))
    if policy.get("schemaVersion") != 1:
        errors.append("unsupported policy schemaVersion")
    for path in (root / "scripts/phase67").glob("*.sh"):
        rc, output = command_result(["bash", "-n", str(path)])
        if rc:
            errors.append(f"shell syntax failed: {path.name}: {output}")
    for path in (root / "scripts/phase67").glob("*.py"):
        rc, output = command_result(["python3", "-m", "py_compile", str(path)])
        if rc:
            errors.append(f"python compile failed: {path.name}: {output}")
    report = {"schemaVersion": 1, "verified": not errors, "requiredFileCount": len(required), "errors": errors}
    if args.output:
        write_json(pathlib.Path(args.output), report)
    print(json.dumps(report, sort_keys=True))
    return 0 if not errors else 2


def add_release(parser: argparse.ArgumentParser, environment: bool = False, mode: bool = False) -> None:
    parser.add_argument("--reference", required=True)
    parser.add_argument("--rc-id", required=True)
    parser.add_argument("--git-commit", required=True)
    parser.add_argument("--application-digest", required=True)
    parser.add_argument("--migration-digest", required=True)
    if environment:
        parser.add_argument("--environment", required=True)
    if mode:
        parser.add_argument("--mode", required=True)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)

    p = sub.add_parser("result")
    p.add_argument("--output", required=True); p.add_argument("--checks", required=True)
    p.add_argument("--phase", required=True); p.add_argument("--name", required=True)
    p.add_argument("--started-at", required=True); p.add_argument("--ended-at", required=True)
    p.add_argument("--forced-status", default="")
    add_release(p, environment=True, mode=True); p.set_defaults(func=cmd_result)

    p = sub.add_parser("prerequisite")
    p.add_argument("--result", required=True); p.add_argument("--phase", required=True)
    add_release(p); p.set_defaults(func=cmd_prerequisite)

    p = sub.add_parser("prerequisites")
    p.add_argument("--root", required=True); p.add_argument("--phase", action="append", required=True)
    add_release(p); p.set_defaults(func=cmd_prerequisites)

    p = sub.add_parser("freeze")
    p.add_argument("--repository", default="."); p.add_argument("--attestation", default="")
    p.add_argument("--minimum-approvers", type=int, default=2); p.add_argument("--maximum-age-seconds", type=int, default=1800)
    p.add_argument("--output", required=True)
    add_release(p, environment=True, mode=True); p.set_defaults(func=cmd_freeze)

    p = sub.add_parser("decision")
    p.add_argument("--policy", required=True); p.add_argument("--input", required=True); p.add_argument("--stage", required=True)
    p.add_argument("--output", required=True); p.add_argument("--allowed-decision", action="append", default=["CONTINUE"])
    add_release(p, environment=True, mode=True); p.set_defaults(func=cmd_decision)

    p = sub.add_parser("event-append")
    p.add_argument("--timeline", required=True); p.add_argument("--event-type", required=True); p.add_argument("--actor", required=True)
    p.add_argument("--message", required=True); p.add_argument("--reference", required=True); p.add_argument("--event-id"); p.add_argument("--recorded-at")
    p.set_defaults(func=cmd_event_append)

    p = sub.add_parser("event-verify")
    p.add_argument("--timeline", required=True); p.add_argument("--output"); p.set_defaults(func=cmd_event_verify)

    p = sub.add_parser("hypercare")
    p.add_argument("--policy", required=True); p.add_argument("--input", required=True); p.add_argument("--output", required=True)
    p.set_defaults(func=cmd_hypercare)

    p = sub.add_parser("bundle")
    p.add_argument("--root", required=True); p.add_argument("--output-dir", required=True)
    add_release(p, environment=True, mode=True); p.set_defaults(func=cmd_bundle)

    p = sub.add_parser("verify-bundle")
    p.add_argument("--archive", required=True); p.add_argument("--checksum", required=True); p.add_argument("--output")
    p.set_defaults(func=cmd_verify_bundle)

    p = sub.add_parser("static")
    p.add_argument("--repository", default="."); p.add_argument("--output"); p.set_defaults(func=cmd_static)
    return parser


def main() -> int:
    args = build_parser().parse_args()
    return int(args.func(args))


if __name__ == "__main__":
    raise SystemExit(main())
