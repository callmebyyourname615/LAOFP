#!/usr/bin/env python3
import argparse
import json
import os
import pathlib
import re

try:
    import yaml
except ImportError as exc:
    raise SystemExit("PyYAML is required") from exc

parser = argparse.ArgumentParser()
parser.add_argument("--template", required=True)
parser.add_argument("--output", required=True)
args = parser.parse_args()

source = pathlib.Path(args.template).read_text(encoding="utf-8")
pattern = re.compile(r"\$\{([A-Z0-9_]+)\}")
missing = sorted({name for name in pattern.findall(source) if name not in os.environ})
if missing:
    raise SystemExit(f"missing manifest variables: {', '.join(missing)}")
rendered = pattern.sub(lambda m: os.environ[m.group(1)], source)
remaining = pattern.findall(rendered)
if remaining:
    raise SystemExit(f"unresolved manifest variables: {remaining}")
document = yaml.safe_load(rendered)
if not isinstance(document, dict):
    raise SystemExit("rendered manifest is not an object")
if document.get("apiVersion") != "chaos-mesh.org/v1alpha1":
    raise SystemExit("unexpected apiVersion")
if document.get("kind") not in {"PodChaos", "NetworkChaos", "DNSChaos", "StressChaos"}:
    raise SystemExit("unsupported Chaos Mesh kind")
metadata = document.get("metadata") or {}
if metadata.get("namespace") != os.environ.get("PHASE73_NAMESPACE"):
    raise SystemExit("rendered namespace does not match PHASE73_NAMESPACE")
path = pathlib.Path(args.output)
path.parent.mkdir(parents=True, exist_ok=True)
path.write_text(yaml.safe_dump(document, sort_keys=False), encoding="utf-8")
print(json.dumps({"output": str(path), "kind": document["kind"], "name": metadata.get("name")}, sort_keys=True))
