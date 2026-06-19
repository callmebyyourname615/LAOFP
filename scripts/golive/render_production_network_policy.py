#!/usr/bin/env python3
"""Render fail-closed production ingress/egress policy from explicit CIDR allowlists."""
from __future__ import annotations

import argparse
import ipaddress
import pathlib
import re

try:
    import yaml
except ImportError as exc:
    raise SystemExit("PyYAML is required") from exc

DNS_LABEL_RE = re.compile(r"^[a-z0-9]([-a-z0-9]*[a-z0-9])?$")


def dns_label(value: str, label: str) -> str:
    if len(value) > 63 or not DNS_LABEL_RE.fullmatch(value):
        raise ValueError(f"invalid {label}")
    return value


def cidrs(value: str, label: str) -> list[str]:
    result = []
    for raw in value.split(","):
        raw = raw.strip()
        if not raw:
            continue
        network = ipaddress.ip_network(raw, strict=False)
        if network.prefixlen == 0:
            raise ValueError(f"{label} cannot allow the entire Internet")
        # Production dependencies must use explicit network ranges, not an
        # unspecified address. Host routes (/32, /128) are allowed.
        if network.is_unspecified:
            raise ValueError(f"{label} contains an unspecified network")
        result.append(str(network))
    if not result:
        raise ValueError(f"{label} requires at least one CIDR")
    return sorted(set(result))


def rules(networks: list[str], ports: list[int]) -> dict:
    return {
        "to": [{"ipBlock": {"cidr": network}} for network in networks],
        "ports": [{"protocol": "TCP", "port": port} for port in ports],
    }


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--database-cidrs", required=True)
    ap.add_argument("--kafka-cidrs", required=True)
    ap.add_argument("--vault-cidrs", required=True)
    ap.add_argument("--object-storage-cidrs", required=True)
    ap.add_argument("--external-api-cidrs", required=True)
    ap.add_argument("--namespace", default="switching")
    ap.add_argument("--name", default="switching-api")
    ap.add_argument("--app-label", default="switching-api")
    ap.add_argument("--track-label")
    ap.add_argument("--kafka-port", type=int, default=9093)
    ap.add_argument("--output", required=True)
    args = ap.parse_args()
    namespace = dns_label(args.namespace, "namespace")
    name = dns_label(args.name, "policy name")
    app_label = dns_label(args.app_label, "app label")
    if args.track_label:
        dns_label(args.track_label, "track label")
    if not (1 <= args.kafka_port <= 65535):
        raise ValueError("invalid Kafka port")

    db = cidrs(args.database_cidrs, "database CIDRs")
    kafka = cidrs(args.kafka_cidrs, "Kafka CIDRs")
    vault = cidrs(args.vault_cidrs, "Vault CIDRs")
    storage = cidrs(args.object_storage_cidrs, "object storage CIDRs")
    external = cidrs(args.external_api_cidrs, "external API CIDRs")
    selector = {"app": app_label}
    if args.track_label:
        selector["track"] = args.track_label
    doc = {
        "apiVersion": "networking.k8s.io/v1",
        "kind": "NetworkPolicy",
        "metadata": {
            "name": name,
            "namespace": namespace,
            "labels": {
                "app": app_label,
                "switching.example.com/environment": "production",
            },
        },
        "spec": {
            "podSelector": {"matchLabels": selector},
            "policyTypes": ["Ingress", "Egress"],
            "ingress": [
                {
                    "from": [{"namespaceSelector": {"matchLabels": {"kubernetes.io/metadata.name": "ingress-nginx"}}}],
                    "ports": [{"protocol": "TCP", "port": 8080}],
                },
                {
                    "from": [{"namespaceSelector": {"matchLabels": {"kubernetes.io/metadata.name": "monitoring"}}}],
                    "ports": [{"protocol": "TCP", "port": 9090}],
                },
            ],
            "egress": [
                {
                    "to": [{"namespaceSelector": {"matchLabels": {"kubernetes.io/metadata.name": "kube-system"}}}],
                    "ports": [{"protocol": "UDP", "port": 53}, {"protocol": "TCP", "port": 53}],
                },
                rules(db, [5432]),
                rules(kafka, [args.kafka_port]),
                rules(vault, [8200]),
                rules(storage, [443]),
                rules(external, [443]),
            ],
        },
    }
    out = pathlib.Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(yaml.safe_dump(doc, sort_keys=False), encoding="utf-8")
    print(out)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
