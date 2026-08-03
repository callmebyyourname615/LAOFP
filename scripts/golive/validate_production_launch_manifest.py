#!/usr/bin/env python3
"""Validate the non-secret Phase 0 production launch declaration."""
from __future__ import annotations

import argparse
import datetime as dt
import re
import sys
from pathlib import Path

import yaml

COMMIT_PATTERN = re.compile(r"^[a-f0-9]{40}$")
DIGEST_PATTERN = re.compile(r"^sha256:[a-f0-9]{64}$")
REFERENCE_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{2,127}$")
PARTICIPANT_PATTERN = re.compile(r"^[A-Z0-9][A-Z0-9_-]{2,34}$")
PLACEHOLDER_PATTERN = re.compile(
    r"(?i)(example|replace|placeholder|your[-_ ]|0123456789abcdef)"
)

REQUIRED_OWNERS = (
    "businessOwner",
    "engineeringLead",
    "qaLead",
    "secOpsLead",
    "sreLead",
    "changeManager",
)
ALLOWED_CHANNELS = {"TRANSFER", "QR", "RTP", "BILL", "CROSS_BORDER"}
ALLOWED_SETTLEMENT_MODELS = {"DNS_T_PLUS_1", "RTGS", "HYBRID"}


def is_placeholder(value: object) -> bool:
    return not isinstance(value, str) or not value.strip() or bool(PLACEHOLDER_PATTERN.search(value))


def load_manifest(path: Path) -> dict:
    loaded = yaml.safe_load(path.read_text(encoding="utf-8"))
    if not isinstance(loaded, dict):
        raise ValueError("manifest must contain a YAML object")
    return loaded


def nested(value: dict, key: str) -> dict:
    item = value.get(key)
    return item if isinstance(item, dict) else {}


def validate(manifest: dict) -> list[str]:
    errors: list[str] = []
    if manifest.get("schemaVersion") != 1:
        errors.append("schemaVersion must be 1")
    if manifest.get("environment") != "production":
        errors.append("environment must be production")
    if manifest.get("template") is True:
        errors.append("template manifest cannot be used as production evidence")

    release = nested(manifest, "release")
    if not isinstance(release.get("reference"), str) or not REFERENCE_PATTERN.fullmatch(release["reference"]):
        errors.append("release.reference is invalid")
    if not isinstance(release.get("gitCommit"), str) or not COMMIT_PATTERN.fullmatch(release["gitCommit"]):
        errors.append("release.gitCommit must be a 40-character lowercase commit")
    for key in ("applicationImageDigest", "rollbackImageDigest"):
        value = release.get(key)
        if not isinstance(value, str) or not DIGEST_PATTERN.fullmatch(value):
            errors.append(f"release.{key} must be a sha256 image digest")
    if release.get("applicationImageDigest") == release.get("rollbackImageDigest"):
        errors.append("rollback image digest must differ from application image digest")

    scope = nested(manifest, "scope")
    participants = scope.get("participants")
    if not isinstance(participants, list) or not participants:
        errors.append("scope.participants must contain at least one participant")
    elif any(not isinstance(item, str) or not PARTICIPANT_PATTERN.fullmatch(item) for item in participants):
        errors.append("scope.participants contains an invalid participant code")
    channels = scope.get("channels")
    if not isinstance(channels, list) or not channels or any(channel not in ALLOWED_CHANNELS for channel in channels):
        errors.append("scope.channels must contain supported production channels")
    currencies = scope.get("currencies")
    if not isinstance(currencies, list) or not currencies or any(not isinstance(currency, str) or not re.fullmatch(r"[A-Z]{3}", currency) for currency in currencies):
        errors.append("scope.currencies must contain ISO currency codes")
    if scope.get("settlementModel") not in ALLOWED_SETTLEMENT_MODELS:
        errors.append("scope.settlementModel is invalid")
    limits = nested(scope, "transactionLimits")
    minimum = limits.get("minimumAmount")
    maximum = limits.get("maximumAmount")
    if limits.get("currency") not in (currencies or []):
        errors.append("scope.transactionLimits.currency must be included in scope.currencies")
    if not isinstance(minimum, (int, float)) or minimum <= 0:
        errors.append("scope.transactionLimits.minimumAmount must be positive")
    if not isinstance(maximum, (int, float)) or not isinstance(minimum, (int, float)) or maximum <= minimum:
        errors.append("scope.transactionLimits.maximumAmount must exceed minimumAmount")

    objectives = nested(manifest, "serviceObjectives")
    for key, limit in (("maximumRpoSeconds", 300), ("maximumRtoSeconds", 1800), ("reconciliationSlaMinutes", 60)):
        value = objectives.get(key)
        if not isinstance(value, (int, float)) or value <= 0 or value > limit:
            errors.append(f"serviceObjectives.{key} must be greater than zero and no more than {limit}")
    for key in ("availabilityPercent", "minimumPaymentSuccessPercent"):
        value = objectives.get(key)
        if not isinstance(value, (int, float)) or value < 99 or value > 100:
            errors.append(f"serviceObjectives.{key} must be between 99 and 100")

    owners = nested(manifest, "owners")
    for key in REQUIRED_OWNERS:
        if is_placeholder(owners.get(key)):
            errors.append(f"owners.{key} must identify an assigned owner")

    change = nested(manifest, "change")
    for key in ("ticketReference", "rollbackOwner", "communicationChannel", "riskRegisterReference"):
        if is_placeholder(change.get(key)):
            errors.append(f"change.{key} must be assigned")
    planned = change.get("plannedDeploymentUtc")
    try:
        parsed = dt.datetime.fromisoformat(str(planned).replace("Z", "+00:00"))
        if parsed.tzinfo is None:
            errors.append("change.plannedDeploymentUtc must include UTC timezone")
    except ValueError:
        errors.append("change.plannedDeploymentUtc must be ISO-8601 UTC")
    if change.get("rollbackOwner") != owners.get("sreLead"):
        errors.append("change.rollbackOwner must match owners.sreLead")

    separation = nested(manifest, "environmentSeparation")
    for key in (
        "uatSeparateFromProduction",
        "stagingSeparateFromProduction",
        "productionDataRestrictedToProduction",
        "productionSecretsExternallyManaged",
    ):
        if separation.get(key) is not True:
            errors.append(f"environmentSeparation.{key} must be true")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", required=True, type=Path)
    args = parser.parse_args()
    try:
        errors = validate(load_manifest(args.manifest))
    except (OSError, ValueError, yaml.YAMLError) as error:
        print(f"Production launch manifest: FAIL ({error})", file=sys.stderr)
        return 2
    if errors:
        print(f"Production launch manifest: FAIL ({len(errors)} issue(s))", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        return 1
    print("Production launch manifest: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
