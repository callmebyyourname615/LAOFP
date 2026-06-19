#!/usr/bin/env python3
"""Render the production canary ingress without deployable placeholder values."""
from __future__ import annotations

import argparse
import pathlib
import re

try:
    import yaml
except ImportError as exc:
    raise SystemExit("PyYAML is required") from exc

DNS_NAME_RE = re.compile(r"^(?=.{1,253}$)(?!-)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$")
DNS_LABEL_RE = re.compile(r"^[a-z0-9]([-a-z0-9]*[a-z0-9])?$")


def label(value: str, name: str) -> str:
    if len(value) > 63 or not DNS_LABEL_RE.fullmatch(value):
        raise ValueError(f"invalid {name}")
    return value


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--template", default="k8s/canary/ingress.yaml")
    ap.add_argument("--namespace", required=True)
    ap.add_argument("--host", required=True)
    ap.add_argument("--tls-secret", required=True)
    ap.add_argument("--client-ca-secret", required=True)
    ap.add_argument("--ingress-class", default="nginx")
    ap.add_argument("--output", required=True)
    args = ap.parse_args()

    namespace = label(args.namespace, "namespace")
    tls_secret = label(args.tls_secret, "TLS secret")
    client_ca = label(args.client_ca_secret, "client CA secret")
    ingress_class = label(args.ingress_class, "ingress class")
    host = args.host.strip().lower().rstrip(".")
    if not DNS_NAME_RE.fullmatch(host):
        raise ValueError("production API host must be a fully qualified DNS name")
    forbidden = ("example.com", "example.org", "example.net", "localhost", ".local")
    if host in forbidden or any(host.endswith("." + suffix) for suffix in forbidden):
        raise ValueError("placeholder or local production API host is forbidden")

    docs = [doc for doc in yaml.safe_load_all(pathlib.Path(args.template).read_text(encoding="utf-8")) if doc]
    if len(docs) != 1 or docs[0].get("kind") != "Ingress":
        raise ValueError("canary ingress template must contain exactly one Ingress")
    doc = docs[0]
    doc.setdefault("metadata", {})["namespace"] = namespace
    annotations = doc["metadata"].setdefault("annotations", {})
    annotations["nginx.ingress.kubernetes.io/auth-tls-secret"] = f"{namespace}/{client_ca}"
    doc.setdefault("spec", {})["ingressClassName"] = ingress_class
    doc["spec"]["tls"] = [{"hosts": [host], "secretName": tls_secret}]
    rules = doc["spec"].get("rules", [])
    if len(rules) != 1:
        raise ValueError("canary ingress template must have exactly one host rule")
    rules[0]["host"] = host
    out = pathlib.Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(yaml.safe_dump(doc, sort_keys=False), encoding="utf-8")
    print(out)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
