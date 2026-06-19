#!/usr/bin/env python3
"""Validate and probe production dependencies without exposing secret values."""
from __future__ import annotations
import argparse
import datetime as dt
import json
import os
import pathlib
import re
import socket
import ssl
import sys
import urllib.parse
import urllib.request
from dataclasses import dataclass, asdict

try:
    import yaml
except ImportError as exc:
    raise SystemExit("PyYAML is required") from exc


@dataclass
class Check:
    id: str
    status: str
    detail: str


def env(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise ValueError(f"required environment variable is missing: {name}")
    return value


def host_from_jdbc(value: str) -> tuple[str, int, str, dict[str, list[str]]]:
    prefix = "jdbc:postgresql://"
    if not value.startswith(prefix):
        raise ValueError("PostgreSQL URL must start with jdbc:postgresql://")
    parsed = urllib.parse.urlparse(value[len("jdbc:"):])
    if not parsed.hostname or not parsed.path.strip("/"):
        raise ValueError("PostgreSQL URL must include host and database")
    return parsed.hostname, parsed.port or 5432, parsed.path.strip("/"), urllib.parse.parse_qs(parsed.query)


def probe_tcp(host: str, port: int, timeout: float) -> None:
    with socket.create_connection((host, port), timeout=timeout):
        return


def tls_expiry(host: str, port: int, timeout: float) -> tuple[int, str]:
    context = ssl.create_default_context()
    with socket.create_connection((host, port), timeout=timeout) as raw:
        with context.wrap_socket(raw, server_hostname=host) as stream:
            cert = stream.getpeercert()
    expires = dt.datetime.strptime(cert["notAfter"], "%b %d %H:%M:%S %Y %Z").replace(tzinfo=dt.timezone.utc)
    days = int((expires - dt.datetime.now(dt.timezone.utc)).total_seconds() // 86400)
    return days, expires.isoformat().replace("+00:00", "Z")


def add(checks: list[Check], check_id: str, fn) -> None:
    try:
        detail = fn()
        checks.append(Check(check_id, "PASS", str(detail or "verified")))
    except Exception as exc:  # fail closed while redacting values
        checks.append(Check(check_id, "FAIL", f"{type(exc).__name__}: {exc}"))


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--contract", default="config/production-infrastructure-contract.yaml")
    ap.add_argument("--output", required=True)
    ap.add_argument("--tls-output")
    ap.add_argument("--connectivity-output")
    ap.add_argument("--timeout-seconds", type=float, default=5.0)
    ap.add_argument("--skip-network", action="store_true", help="schema-only validation for CI tests")
    args = ap.parse_args()
    contract = yaml.safe_load(pathlib.Path(args.contract).read_text(encoding="utf-8"))
    checks: list[Check] = []
    tls_rows: list[dict] = []
    connectivity: list[dict] = []
    forbidden = [re.compile(pattern, re.I) for pattern in contract.get("forbiddenHostPatterns", [])]

    def validate_host(host: str) -> None:
        if any(pattern.search(host) for pattern in forbidden):
            raise ValueError(f"forbidden production host pattern: {host}")

    db = contract["checks"]["database"]
    parsed_db: dict[str, tuple[str, int, str, dict]] = {}
    for key, env_name in (("primary", db["primaryUrlEnv"]), ("replica", db["replicaUrlEnv"])):
        def check_db(key=key, env_name=env_name):
            host, port, database, query = host_from_jdbc(env(env_name))
            validate_host(host)
            if db.get("requireTlsVerifyFull") and query.get("sslmode") != ["verify-full"]:
                raise ValueError("sslmode=verify-full is required")
            if database != db.get("expectedDatabase"):
                raise ValueError("unexpected database name")
            parsed_db[key] = (host, port, database, query)
            return f"{key} PostgreSQL URL contract valid"
        add(checks, f"database-{key}-contract", check_db)
    add(checks, "database-identities-separated", lambda: (
        "application and Flyway identities differ"
        if env(db["applicationUserEnv"]) != env(db["flywayUserEnv"])
        else (_ for _ in ()).throw(ValueError("application and Flyway users must differ"))
    ))

    kafka = contract["checks"]["kafka"]
    brokers: list[tuple[str, int]] = []
    def check_kafka():
        for raw in env(kafka["bootstrapServersEnv"]).split(","):
            host, separator, port = raw.strip().rpartition(":")
            if not separator or not host or not port.isdigit():
                raise ValueError("invalid Kafka bootstrap server")
            validate_host(host)
            brokers.append((host, int(port)))
        if len({host + ":" + str(port) for host, port in brokers}) < int(kafka["minimumBrokers"]):
            raise ValueError("insufficient unique Kafka brokers")
        protocol = env(kafka["securityProtocolEnv"])
        if protocol not in kafka["allowedSecurityProtocols"]:
            raise ValueError("Kafka security protocol is not allowed")
        if kafka.get("requireHostnameVerification") and os.environ.get("KAFKA_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM") != "https":
            raise ValueError("Kafka hostname verification must be https")
        return f"{len(brokers)} Kafka brokers with TLS contract"
    add(checks, "kafka-contract", check_kafka)

    vault = contract["checks"]["vault"]
    vault_url = ""
    def check_vault_contract():
        nonlocal vault_url
        vault_url = env(vault["addressEnv"])
        parsed = urllib.parse.urlparse(vault_url)
        if parsed.scheme != "https" or not parsed.hostname:
            raise ValueError("Vault address must be HTTPS")
        validate_host(parsed.hostname)
        if env(vault["namespaceEnv"]) == "root":
            raise ValueError("dedicated Vault namespace is required")
        if os.environ.get("WEBHOOK_VAULT_AUTH_METHOD") != vault["authMethod"]:
            raise ValueError("Vault Kubernetes auth is required")
        env(vault["transitMountEnv"]); env(vault["transitKeyEnv"])
        return "Vault contract valid"
    add(checks, "vault-contract", check_vault_contract)

    storage = contract["checks"]["objectStorage"]
    def check_storage():
        endpoint = urllib.parse.urlparse(env(storage["endpointEnv"]))
        if storage.get("requireHttps") and endpoint.scheme != "https":
            raise ValueError("object storage endpoint must be HTTPS")
        if not endpoint.hostname:
            raise ValueError("object storage endpoint has no hostname")
        validate_host(endpoint.hostname)
        env(storage["bucketEnv"])
        return "object storage endpoint and bucket contract valid"
    add(checks, "object-storage-contract", check_storage)

    network = contract["checks"]["network"]
    dns_names = [item.strip() for item in env(network["requiredDnsNamesEnv"]).split(",") if item.strip()]
    tcp_targets: list[tuple[str, int]] = []
    for raw in [item.strip() for item in env(network["requiredTcpTargetsEnv"]).split(",") if item.strip()]:
        host, separator, port = raw.rpartition(":")
        if not separator or not port.isdigit():
            checks.append(Check("network-target-format", "FAIL", "invalid TCP target format"))
            continue
        validate_host(host)
        tcp_targets.append((host, int(port)))

    tls = contract["checks"]["tls"]
    tls_targets: list[tuple[str, int]] = []
    for raw in [item.strip() for item in env(tls["endpointsEnv"]).split(",") if item.strip()]:
        host, separator, port = raw.rpartition(":")
        if not separator or not port.isdigit():
            checks.append(Check("tls-target-format", "FAIL", "invalid TLS endpoint format"))
            continue
        validate_host(host)
        tls_targets.append((host, int(port)))

    if not args.skip_network:
        for host in dns_names:
            add(checks, f"dns-{host}", lambda host=host: f"resolved {len(socket.getaddrinfo(host, None))} addresses")
        all_tcp = list(dict.fromkeys(tcp_targets + [(h, p) for h, p, *_ in parsed_db.values()] + brokers))
        for host, port in all_tcp:
            check_id = re.sub(r"[^a-z0-9]+", "-", f"tcp-{host}-{port}".lower()).strip("-")
            try:
                probe_tcp(host, port, args.timeout_seconds)
                connectivity.append({"host": host, "port": port, "status": "PASS"})
                checks.append(Check(check_id, "PASS", "TCP connection established"))
            except Exception as exc:
                connectivity.append({"host": host, "port": port, "status": "FAIL", "error": type(exc).__name__})
                checks.append(Check(check_id, "FAIL", f"TCP probe failed: {type(exc).__name__}"))
        for host, port in tls_targets:
            check_id = re.sub(r"[^a-z0-9]+", "-", f"tls-{host}-{port}".lower()).strip("-")
            try:
                days, expires = tls_expiry(host, port, args.timeout_seconds)
                status = "PASS" if days >= int(tls["minimumValidityDays"]) else "FAIL"
                tls_rows.append({"host": host, "port": port, "daysRemaining": days, "notAfter": expires, "status": status})
                checks.append(Check(check_id, status, f"certificate valid for {days} days"))
            except Exception as exc:
                tls_rows.append({"host": host, "port": port, "status": "FAIL", "error": type(exc).__name__})
                checks.append(Check(check_id, "FAIL", f"TLS probe failed: {type(exc).__name__}"))
        if vault_url:
            def probe_vault():
                request = urllib.request.Request(vault_url.rstrip("/") + "/v1/sys/health", headers={"Accept": "application/json"})
                context = ssl.create_default_context()
                with urllib.request.urlopen(request, timeout=args.timeout_seconds, context=context) as response:
                    if response.status not in (200, 429, 472, 473):
                        raise ValueError(f"unexpected Vault health status {response.status}")
                return "Vault health endpoint reachable"
            add(checks, "vault-health", probe_vault)

    passed = all(check.status == "PASS" for check in checks) and bool(checks)
    report = {
        "schemaVersion": 1,
        "contractId": contract["contractId"],
        "status": "PASS" if passed else "FAIL",
        "networkProbesSkipped": args.skip_network,
        "checks": [asdict(check) for check in checks],
    }
    pathlib.Path(args.output).write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if args.tls_output:
        pathlib.Path(args.tls_output).write_text(json.dumps({"schemaVersion": 1, "endpoints": tls_rows}, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if args.connectivity_output:
        pathlib.Path(args.connectivity_output).write_text(json.dumps({"schemaVersion": 1, "targets": connectivity}, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({"status": report["status"], "checks": len(checks)}, sort_keys=True))
    return 0 if passed else 2


if __name__ == "__main__":
    raise SystemExit(main())
