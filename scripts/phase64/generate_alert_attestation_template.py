#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

import yaml


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--reference", default="replace-with-release-reference")
    parser.add_argument("--commit", default="0" * 40)
    parser.add_argument("--application-digest", default="sha256:" + "0" * 64)
    args = parser.parse_args()
    alerts = set()
    for path in sorted((args.root / "monitoring/prometheus").glob("*.yaml")):
        for document in yaml.safe_load_all(path.read_text(encoding="utf-8")):
            if not isinstance(document, dict) or document.get("kind") != "PrometheusRule":
                continue
            for group in document.get("spec", {}).get("groups", []):
                for rule in group.get("rules", []):
                    if rule.get("alert"):
                        alerts.add(str(rule["alert"]))
    document = {
        "schemaVersion": 1,
        "release": {
            "reference": args.reference,
            "gitCommit": args.commit,
            "applicationImageDigest": args.application_digest,
        },
        "alerts": [
            {
                "name": name,
                "fired": False,
                "routed": False,
                "resolved": False,
                "receiver": "",
                "evidence": "",
            }
            for name in sorted(alerts)
        ],
        "signedBy": "",
        "signedAt": "",
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(document, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"Alert attestation template generated: {len(alerts)} alerts")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
