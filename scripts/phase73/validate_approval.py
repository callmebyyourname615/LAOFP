#!/usr/bin/env python3
import argparse
import datetime as dt
import json
import pathlib
import re

ALL_SCENARIOS = {
    "pod-kill", "database-network-loss", "kafka-network-delay",
    "object-storage-network-loss", "external-api-delay", "dns-error",
    "cpu-stress", "memory-stress",
}

parser = argparse.ArgumentParser()
parser.add_argument("--approval", required=True)
parser.add_argument("--token", required=True)
parser.add_argument("--scenario")
parser.add_argument("--maximum-age-minutes", type=int, default=120)
parser.add_argument("--required-scenarios-json", default="[]")
args = parser.parse_args()

doc = json.loads(pathlib.Path(args.approval).read_text(encoding="utf-8"))
errors = []
if doc.get("schemaVersion") != 1:
    errors.append("schemaVersion must be 1")
if doc.get("approved") is not True:
    errors.append("approved must be true")
if doc.get("environment") != "uat":
    errors.append("environment must be uat")
token = str(doc.get("tokenId", ""))
if token != args.token:
    errors.append("CHAOS_APPROVAL_TOKEN does not match approval tokenId")
if not re.fullmatch(r"CHAOS-UAT-[A-Z0-9-]{8,64}", token):
    errors.append("tokenId format is invalid")
scenarios = set(doc.get("scenarios") or [])
try:
    required_scenarios = set(json.loads(args.required_scenarios_json))
except json.JSONDecodeError as exc:
    errors.append(f"required scenarios JSON is invalid: {exc}")
    required_scenarios = set()
missing_required = required_scenarios - scenarios
if missing_required:
    errors.append(f"approval is missing required scenarios: {sorted(missing_required)}")
unknown = scenarios - ALL_SCENARIOS
if unknown:
    errors.append(f"unknown scenarios: {sorted(unknown)}")
if args.scenario and args.scenario not in scenarios:
    errors.append(f"scenario is not approved: {args.scenario}")

def parse_timestamp(name: str) -> dt.datetime | None:
    raw = doc.get(name)
    if not isinstance(raw, str):
        errors.append(f"{name} is required")
        return None
    try:
        parsed = dt.datetime.fromisoformat(raw.replace("Z", "+00:00"))
    except ValueError:
        errors.append(f"{name} is not ISO-8601")
        return None
    if parsed.tzinfo is None:
        errors.append(f"{name} must include timezone")
        return None
    return parsed.astimezone(dt.timezone.utc)

approved_at = parse_timestamp("approvedAt")
expires_at = parse_timestamp("expiresAt")
now = dt.datetime.now(dt.timezone.utc)
if approved_at and now - approved_at > dt.timedelta(minutes=args.maximum_age_minutes):
    errors.append("approval is older than maximumAgeMinutes")
if approved_at and approved_at > now + dt.timedelta(minutes=5):
    errors.append("approvedAt is in the future")
if expires_at and expires_at <= now:
    errors.append("approval has expired")
if approved_at and expires_at and expires_at <= approved_at:
    errors.append("expiresAt must be after approvedAt")
if not str(doc.get("approvedBy", "")).strip():
    errors.append("approvedBy is required")
if not str(doc.get("changeReference", "")).strip():
    errors.append("changeReference is required")
if errors:
    raise SystemExit("approval validation failed: " + "; ".join(errors))
print(json.dumps({"valid": True, "tokenId": token, "scenario": args.scenario, "expiresAt": doc["expiresAt"]}, sort_keys=True))
