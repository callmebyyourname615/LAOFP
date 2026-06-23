#!/usr/bin/env python3
import argparse
import json
import pathlib
import sys

try:
    import yaml
except ImportError as exc:
    raise SystemExit("PyYAML is required: python -m pip install PyYAML==6.0.2") from exc

parser = argparse.ArgumentParser()
parser.add_argument("--policy", required=True)
parser.add_argument("--path", required=True)
parser.add_argument("--json", action="store_true")
args = parser.parse_args()

data = yaml.safe_load(pathlib.Path(args.policy).read_text(encoding="utf-8"))
value = data
for part in args.path.split("."):
    if isinstance(value, dict) and part in value:
        value = value[part]
    else:
        raise SystemExit(f"policy path not found: {args.path}")
if args.json:
    print(json.dumps(value, separators=(",", ":")))
elif isinstance(value, bool):
    print(str(value).lower())
elif value is None:
    print("")
else:
    print(value)
