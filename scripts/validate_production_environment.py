#!/usr/bin/env python3
"""Validate the production environment contract without printing secret values."""
from __future__ import annotations

import argparse
import base64
import ipaddress
import os
import re
import sys
from pathlib import Path
from urllib.parse import urlparse

import yaml

LOCAL_MARKERS = ("localhost", "127.0.0.1", "0.0.0.0", "mock-", ".local")


def parse_env_file(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for line_number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            raise ValueError(f"{path}:{line_number}: expected KEY=VALUE")
        key, value = line.split("=", 1)
        key = key.strip()
        if not re.fullmatch(r"[A-Z][A-Z0-9_]*", key):
            raise ValueError(f"{path}:{line_number}: invalid environment key")
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in {'"', "'"}:
            value = value[1:-1]
        if key in values:
            raise ValueError(f"{path}:{line_number}: duplicate key {key}")
        values[key] = value
    return values


def placeholder(value: str, patterns: list[re.Pattern[str]]) -> bool:
    return any(pattern.search(value) for pattern in patterns)


def validate_url(name: str, value: str, *, https: bool = False) -> list[str]:
    errors: list[str] = []
    parsed = urlparse(value)
    if not parsed.scheme or not parsed.hostname:
        return [f"{name} must be an absolute URL"]
    if https and parsed.scheme.lower() != "https":
        errors.append(f"{name} must use HTTPS")
    lowered = value.lower()
    if any(marker in lowered for marker in LOCAL_MARKERS):
        errors.append(f"{name} must not reference local/mock infrastructure")
    return errors


def validate_rule(name: str, value: str, rule: dict, template: bool,
                  patterns: list[re.Pattern[str]]) -> list[str]:
    errors: list[str] = []
    kind = rule["type"]
    is_placeholder = placeholder(value, patterns)

    if template and is_placeholder:
        if not value:
            errors.append(f"{name} template value is blank")
        return errors
    if is_placeholder:
        errors.append(f"{name} contains a placeholder/development value")
        return errors
    if not value:
        return [f"{name} is blank"]
    if "${" in value and not template:
        errors.append(f"{name} contains an unresolved variable reference")

    expected_literal = (str(rule.get("value")).lower()
                        if isinstance(rule.get("value"), bool)
                        else str(rule.get("value")))
    if kind == "literal" and value != expected_literal:
        errors.append(f"{name} must equal the contract value")
    elif kind == "boolean":
        expected = str(rule.get("value", "")).lower()
        if value.lower() not in {"true", "false"} or (expected and value.lower() != expected):
            errors.append(f"{name} must be {expected or 'true/false'}")
    elif kind == "enum" and value not in rule["values"]:
        errors.append(f"{name} must be one of {', '.join(rule['values'])}")
    elif kind == "secret":
        if len(value) < int(rule.get("minLength", 1)):
            errors.append(f"{name} does not meet minimum secret length")
    elif kind == "base64-bytes":
        try:
            decoded = base64.b64decode(value, validate=True)
            if len(decoded) != int(rule["decodedLength"]):
                errors.append(f"{name} must decode to {rule['decodedLength']} bytes")
        except Exception:
            errors.append(f"{name} must be valid Base64")
    elif kind == "https-url":
        errors.extend(validate_url(name, value, https=True))
    elif kind == "postgres-jdbc-verify-full":
        lowered = value.lower()
        if not lowered.startswith("jdbc:postgresql://"):
            errors.append(f"{name} must be a PostgreSQL JDBC URL")
        if "sslmode=verify-full" not in lowered:
            errors.append(f"{name} must use sslmode=verify-full")
        if "sslrootcert=" not in lowered:
            errors.append(f"{name} must provide sslrootcert")
        if any(marker in lowered for marker in LOCAL_MARKERS):
            errors.append(f"{name} must not reference local/mock infrastructure")
    elif kind == "kafka-bootstrap-tls":
        brokers = [part.strip() for part in value.split(",") if part.strip()]
        if len(brokers) < 3:
            errors.append(f"{name} must contain at least three brokers")
        for broker in brokers:
            if not re.fullmatch(r"[A-Za-z0-9.-]+:[1-9][0-9]{1,4}", broker):
                errors.append(f"{name} contains an invalid broker entry")
                break
            if any(marker in broker.lower() for marker in LOCAL_MARKERS):
                errors.append(f"{name} must not reference local/mock infrastructure")
                break
    elif kind == "kafka-jaas-secret":
        if "username=" not in value or "password=" not in value:
            errors.append(f"{name} must contain username and password assignments")
        if len(value) < int(rule.get("minLength", 1)):
            errors.append(f"{name} is incomplete")
    elif kind == "absolute-path" and not value.startswith("/"):
        errors.append(f"{name} must be an absolute path")
    elif kind == "identifier" and not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._/-]{1,127}", value):
        errors.append(f"{name} contains unsupported identifier characters")
    elif kind == "duration" and not re.fullmatch(r"P(?:T)?[0-9A-Z.]+", value):
        errors.append(f"{name} must be an ISO-8601 duration")
    elif kind == "hostname":
        if not re.fullmatch(r"[A-Za-z0-9](?:[A-Za-z0-9.-]{0,251}[A-Za-z0-9])?", value):
            errors.append(f"{name} must be a hostname")
        if any(marker in value.lower() for marker in LOCAL_MARKERS):
            errors.append(f"{name} must not reference local/mock infrastructure")
    elif kind == "host-list":
        hosts = [part.strip() for part in value.split(",") if part.strip()]
        if not hosts:
            errors.append(f"{name} must contain at least one host")
        for host in hosts:
            try:
                ipaddress.ip_address(host)
                errors.append(f"{name} must use approved DNS hostnames, not IP literals")
                break
            except ValueError:
                pass
            if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9.-]+", host):
                errors.append(f"{name} contains an invalid hostname")
                break
    elif kind == "nonempty":
        pass
    return errors


def load_yaml(path: Path) -> dict:
    data = yaml.safe_load(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise ValueError(f"{path} must contain a YAML object")
    return data


def verify_k8s(contract: dict, root: Path) -> list[str]:
    configmap = load_yaml(root / "k8s/configmap.yaml")
    app_secret = load_yaml(root / "k8s/external-secrets/application-secrets.yaml")
    migration_secret = load_yaml(root / "k8s/external-secrets/migration-secrets.yaml")
    config_keys = set((configmap.get("data") or {}).keys())
    app_keys = {row.get("secretKey") for row in app_secret.get("spec", {}).get("data", [])}
    migration_keys = {row.get("secretKey") for row in migration_secret.get("spec", {}).get("data", [])}
    sources = {
        "configmap": config_keys,
        "application-secret": app_keys,
        "migration-secret": migration_keys,
    }
    errors = []
    for key, rule in contract["variables"].items():
        source = rule["delivery"]
        if key not in sources[source]:
            errors.append(f"{key} is not delivered by {source}")
    for forbidden in contract.get("forbiddenKeys", []):
        if any(forbidden in keys for keys in sources.values()):
            errors.append(f"forbidden key {forbidden} is present in Kubernetes delivery manifests")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--contract", default="config/production-environment-contract.yaml")
    source = parser.add_mutually_exclusive_group(required=False)
    source.add_argument("--env-file")
    source.add_argument("--from-environment", action="store_true")
    parser.add_argument("--template", action="store_true")
    parser.add_argument("--verify-k8s", action="store_true")
    parser.add_argument("--root", default=".")
    args = parser.parse_args()

    root = Path(args.root).resolve()
    contract = load_yaml(root / args.contract)
    if contract.get("schemaVersion") != 1:
        raise SystemExit("unsupported production environment contract schema")
    patterns = [re.compile(pattern) for pattern in contract.get("placeholderPatterns", [])]

    errors: list[str] = []
    values: dict[str, str] = {}
    if args.env_file:
        values = parse_env_file(Path(args.env_file))
    elif args.from_environment:
        values = dict(os.environ)
    elif not args.verify_k8s:
        parser.error("one of --env-file, --from-environment or --verify-k8s is required")

    if values:
        for forbidden in contract.get("forbiddenKeys", []):
            if values.get(forbidden):
                errors.append(f"forbidden key {forbidden} must not be set")
        for name, rule in contract["variables"].items():
            if name not in values:
                errors.append(f"missing required variable {name}")
                continue
            errors.extend(validate_rule(name, values[name], rule, args.template, patterns))
        if not args.template:
            for check in contract.get("crossChecks", []):
                keys = check.get("keys", [])
                if check.get("type") == "different" and len(keys) == 2:
                    if values.get(keys[0]) and values.get(keys[0]) == values.get(keys[1]):
                        errors.append(f"{keys[0]} and {keys[1]} must be different")

    if args.verify_k8s:
        errors.extend(verify_k8s(contract, root))

    if errors:
        print(f"Production environment contract: FAIL ({len(errors)} issue(s))", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        return 1
    print("Production environment contract: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
