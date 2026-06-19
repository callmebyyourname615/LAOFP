#!/usr/bin/env bash
set -Eeuo pipefail

: "${ALERTMANAGER_URL:?ALERTMANAGER_URL is required}"
: "${ALERT_EXPECTED_RECEIVER:?ALERT_EXPECTED_RECEIVER is required}"
: "${ALERT_DRILL_CONFIRMATION:?Set ALERT_DRILL_CONFIRMATION=I_UNDERSTAND_THIS_SENDS_TEST_ALERTS}"
[[ "$ALERT_DRILL_CONFIRMATION" == I_UNDERSTAND_THIS_SENDS_TEST_ALERTS ]] || { echo "Invalid drill confirmation" >&2; exit 64; }
[[ "$ALERTMANAGER_URL" == https://* ]] || { echo "Alertmanager must use HTTPS" >&2; exit 64; }
command -v curl >/dev/null
command -v python3 >/dev/null

OUTPUT="${ALERT_DELIVERY_OUTPUT:-build/alert-delivery-results.json}"
mkdir -p "$(dirname "$OUTPUT")"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

python3 - "$TMP/alerts.json" <<'PY'
import datetime, json, pathlib, yaml, sys
root = pathlib.Path("monitoring/prometheus")
names = set()
for path in sorted(root.glob("*.yaml")):
    doc = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
    if doc.get("kind") != "PrometheusRule":
        continue
    for group in doc.get("spec", {}).get("groups", []):
        for rule in group.get("rules", []):
            if rule.get("alert"):
                names.add(rule["alert"])
now = datetime.datetime.now(datetime.timezone.utc)
end = now + datetime.timedelta(minutes=15)
alerts = [{
    "labels": {"alertname": name, "severity": "warning", "environment": "drill", "drill": "true"},
    "annotations": {"summary": f"Controlled delivery drill for {name}", "description": "Synthetic alert; no production incident."},
    "startsAt": now.isoformat().replace("+00:00", "Z"),
    "endsAt": end.isoformat().replace("+00:00", "Z"),
    "generatorURL": "https://switching.internal/runbooks/alert-delivery-drill"
} for name in sorted(names)]
pathlib.Path(sys.argv[1]).write_text(json.dumps(alerts), encoding="utf-8")
print(len(alerts))
PY

count="$(python3 -c 'import json,sys; print(len(json.load(open(sys.argv[1]))))' "$TMP/alerts.json")"
[[ "$count" -gt 0 ]] || { echo "No alert rules found" >&2; exit 1; }

curl_args=(--fail-with-body --silent --show-error -H 'Content-Type: application/json')
[[ -n "${ALERTMANAGER_BEARER_TOKEN:-}" ]] && curl_args+=(-H "Authorization: Bearer ${ALERTMANAGER_BEARER_TOKEN}")
curl "${curl_args[@]}" -X POST --data-binary @"$TMP/alerts.json" "${ALERTMANAGER_URL%/}/api/v2/alerts" >/dev/null
sleep "${ALERT_DRILL_SETTLE_SECONDS:-5}"
curl "${curl_args[@]}" "${ALERTMANAGER_URL%/}/api/v2/alerts/groups" > "$TMP/groups.json"

python3 - "$TMP/alerts.json" "$TMP/groups.json" "$ALERT_EXPECTED_RECEIVER" "$OUTPUT" <<'PY'
import datetime, json, pathlib, sys
alerts, groups, expected, output = sys.argv[1:]
wanted = {a["labels"]["alertname"] for a in json.load(open(alerts, encoding="utf-8"))}
seen = set()
receivers = {}
for group in json.load(open(groups, encoding="utf-8")):
    receiver = (group.get("receiver") or {}).get("name", "")
    for alert in group.get("alerts", []):
        labels = alert.get("labels", {})
        if labels.get("drill") == "true" and labels.get("alertname") in wanted:
            name = labels["alertname"]
            seen.add(name)
            receivers[name] = receiver
wrong = sorted(name for name, receiver in receivers.items() if receiver != expected)
missing = sorted(wanted - seen)
doc = {
    "generatedAt": datetime.datetime.now(datetime.timezone.utc).isoformat().replace("+00:00", "Z"),
    "expectedReceiver": expected,
    "alertCount": len(wanted),
    "observedCount": len(seen),
    "missing": missing,
    "wrongReceiver": wrong,
    "passed": not missing and not wrong,
}
pathlib.Path(output).write_text(json.dumps(doc, indent=2, sort_keys=True) + "\n", encoding="utf-8")
if missing or wrong:
    raise SystemExit(1)
PY

# Resolve synthetic alerts after verification so they do not remain firing.
python3 - "$TMP/alerts.json" "$TMP/resolved.json" <<'PY'
import datetime, json, pathlib, sys
alerts = json.load(open(sys.argv[1], encoding="utf-8"))
now = datetime.datetime.now(datetime.timezone.utc).isoformat().replace("+00:00", "Z")
for alert in alerts:
    alert["endsAt"] = now
pathlib.Path(sys.argv[2]).write_text(json.dumps(alerts), encoding="utf-8")
PY
curl "${curl_args[@]}" -X POST --data-binary @"$TMP/resolved.json" "${ALERTMANAGER_URL%/}/api/v2/alerts" >/dev/null
cat "$OUTPUT"
