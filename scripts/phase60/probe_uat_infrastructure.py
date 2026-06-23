#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import re
import socket
import ssl
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlparse


def parse_env(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        values[key.strip()] = value
    return values


def tcp_probe(name: str, host: str, port: int, timeout: float) -> dict:
    result = {"name": name, "kind": "tcp", "host": host, "port": port, "passed": False}
    try:
        with socket.create_connection((host, port), timeout=timeout):
            result["passed"] = True
    except Exception as exc:
        result["error"] = type(exc).__name__
    return result


def tls_probe(name: str, host: str, port: int, timeout: float, minimum_days: int) -> dict:
    result = {"name": name, "kind": "tls", "host": host, "port": port, "passed": False}
    try:
        context = ssl.create_default_context()
        with socket.create_connection((host, port), timeout=timeout) as raw:
            with context.wrap_socket(raw, server_hostname=host) as secured:
                certificate = secured.getpeercert()
        expires = datetime.strptime(certificate["notAfter"], "%b %d %H:%M:%S %Y %Z").replace(tzinfo=timezone.utc)
        days = int((expires - datetime.now(timezone.utc)).total_seconds() // 86400)
        result.update({
            "subject": dict(item[0] for item in certificate.get("subject", [])),
            "issuer": dict(item[0] for item in certificate.get("issuer", [])),
            "expiresAt": expires.isoformat().replace("+00:00", "Z"),
            "daysRemaining": days,
            "passed": days >= minimum_days,
        })
        if days < minimum_days:
            result["error"] = f"certificate has fewer than {minimum_days} days remaining"
    except Exception as exc:
        result["error"] = type(exc).__name__
    return result


def http_probe(name: str, url: str, timeout: float, expected_statuses: set[int], headers: dict[str, str] | None = None) -> dict:
    result = {"name": name, "kind": "http", "url": url, "passed": False}
    request = urllib.request.Request(url, headers=headers or {})
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            body = response.read(64 * 1024)
            status = response.status
    except urllib.error.HTTPError as exc:
        body = exc.read(64 * 1024)
        status = exc.code
    except Exception as exc:
        result["error"] = type(exc).__name__
        return result
    result["status"] = status
    result["passed"] = status in expected_statuses
    content_type = ""
    try:
        content_type = response.headers.get("Content-Type", "")  # type: ignore[name-defined]
    except Exception:
        pass
    if "json" in content_type.lower() or body.lstrip().startswith((b"{", b"[")):
        try:
            parsed = json.loads(body)
            if isinstance(parsed, dict):
                result["responseKeys"] = sorted(parsed)[:20]
                if name == "application-health":
                    result["applicationStatus"] = parsed.get("status")
                    result["passed"] = result["passed"] and parsed.get("status") == "UP"
        except Exception:
            result["jsonParse"] = "failed"
    return result


def jdbc_host_port(value: str) -> tuple[str, int] | None:
    match = re.match(r"jdbc:postgresql://([^/:?]+)(?::(\d+))?/", value)
    if not match:
        return None
    return match.group(1), int(match.group(2) or 5432)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--env-file", type=Path, required=True)
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--timeout", type=float, default=5.0)
    parser.add_argument("--minimum-certificate-days", type=int, default=30)
    parser.add_argument("--health-header-name")
    args = parser.parse_args()

    values = parse_env(args.env_file)
    checks: list[dict] = []
    base = args.base_url.rstrip("/")
    headers = {}
    health_header_value = os.environ.get("UAT_HEALTH_HEADER_VALUE", "")
    if args.health_header_name and health_header_value:
        headers[args.health_header_name] = health_header_value
    checks.append(http_probe(
        "application-health", base + "/actuator/health", args.timeout, {200}, headers))

    base_parsed = urlparse(base)
    if base_parsed.scheme == "https" and base_parsed.hostname:
        checks.append(tls_probe(
            "application-tls", base_parsed.hostname, base_parsed.port or 443,
            args.timeout, args.minimum_certificate_days))
    else:
        checks.append({"name": "application-tls", "kind": "tls", "passed": False, "error": "UAT base URL must use HTTPS"})

    database = jdbc_host_port(values.get("DB_URL", ""))
    if database:
        checks.append(tcp_probe("postgres-primary", database[0], database[1], args.timeout))
    else:
        checks.append({"name": "postgres-primary", "kind": "tcp", "passed": False, "error": "DB_URL could not be parsed"})

    archive = jdbc_host_port(values.get("ARCHIVE_DB_URL", ""))
    if archive:
        checks.append(tcp_probe("postgres-archive", archive[0], archive[1], args.timeout))

    brokers = [item.strip() for item in values.get("SPRING_KAFKA_BOOTSTRAP_SERVERS", "").split(",") if item.strip()]
    for index, broker in enumerate(brokers):
        host, separator, port_text = broker.rpartition(":")
        if separator and port_text.isdigit():
            checks.append(tcp_probe(f"kafka-broker-{index + 1}", host, int(port_text), args.timeout))
        else:
            checks.append({"name": f"kafka-broker-{index + 1}", "kind": "tcp", "passed": False, "error": "invalid broker address"})

    for name, key, suffix, statuses in (
        ("vault-health", "VAULT_ADDR", "/v1/sys/health", {200, 429, 472, 473}),
        ("object-storage", "OBJECT_STORAGE_ENDPOINT", "/", {200, 204, 301, 302, 307, 403}),
    ):
        endpoint = values.get(key)
        if endpoint:
            checks.append(http_probe(name, endpoint.rstrip("/") + suffix, args.timeout, statuses))
        else:
            checks.append({"name": name, "kind": "http", "passed": False, "error": f"{key} is missing"})

    passed = all(check.get("passed") is True for check in checks)
    document = {
        "schemaVersion": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "baseUrl": base,
        "passed": passed,
        "checks": checks,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(document, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"UAT infrastructure probe: {'PASS' if passed else 'FAIL'}")
    for check in checks:
        print(f"  {'PASS' if check.get('passed') else 'FAIL'} {check['name']}")
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
