#!/usr/bin/env python3
"""Verify health, metrics, dashboards, alert inventory and delivery evidence."""
from __future__ import annotations

import argparse
import glob
import json
import pathlib

import yaml


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", default="config/observability-certification.yaml")
    parser.add_argument("--health-dir", required=True)
    parser.add_argument("--metrics-file", required=True)
    parser.add_argument("--alert-delivery-file", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    config = yaml.safe_load(pathlib.Path(args.config).read_text(encoding="utf-8"))
    reasons: list[str] = []
    health: dict[str, dict] = {}
    health_dir = pathlib.Path(args.health_dir)
    root_health: dict = {}
    for endpoint in config["healthEndpoints"]:
        name = endpoint.strip("/").replace("/", "-") + ".json"
        path = health_dir / name
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
            ok = data.get("status") == "UP"
        except Exception:
            data, ok = {}, False
        if endpoint == "/actuator/health":
            root_health = data
        health[endpoint] = {"path": str(path), "status": data.get("status"), "passed": ok}
        if not ok:
            reasons.append(f"health endpoint not UP: {endpoint}")

    component_status = {}
    components = root_health.get("components", {}) if isinstance(root_health, dict) else {}
    for component in config.get("requiredHealthComponents", []):
        status = (components.get(component) or {}).get("status")
        component_status[component] = status
        if status != "UP":
            reasons.append(f"required health component not UP or hidden: {component}")

    metrics_path = pathlib.Path(args.metrics_file)
    metrics_text = metrics_path.read_text(encoding="utf-8") if metrics_path.is_file() else ""
    metric_status = {name: name in metrics_text for name in config["requiredMetricFamilies"]}
    reasons.extend(f"missing metric family: {name}" for name, found in metric_status.items() if not found)

    dashboards = {}
    for path_text in config["dashboards"]:
        try:
            data = json.loads(pathlib.Path(path_text).read_text(encoding="utf-8"))
            dashboard = data.get("dashboard", data)
            title = dashboard.get("title")
            panels = dashboard.get("panels", [])
            ok = bool(title) and isinstance(panels, list) and bool(panels)
            dashboards[path_text] = {"title": title, "panelCount": len(panels) if isinstance(panels, list) else 0, "passed": ok}
        except Exception:
            dashboards[path_text] = {"title": None, "panelCount": 0, "passed": False}
            ok = False
        if not ok:
            reasons.append(f"invalid or empty dashboard: {path_text}")

    alerts = set()
    for pattern in config["alertRuleGlobs"]:
        for path_text in glob.glob(pattern):
            for document in yaml.safe_load_all(pathlib.Path(path_text).read_text(encoding="utf-8")):
                if not isinstance(document, dict) or document.get("kind") != "PrometheusRule":
                    continue
                for group in document.get("spec", {}).get("groups", []):
                    for rule in group.get("rules", []):
                        if rule.get("alert"):
                            alerts.add(rule["alert"])
    minimum_alerts = int(config.get("minimumAlertRuleCount", 1))
    if len(alerts) < minimum_alerts:
        reasons.append(f"alert rule count {len(alerts)} is below required {minimum_alerts}")

    try:
        delivery = json.loads(pathlib.Path(args.alert_delivery_file).read_text(encoding="utf-8"))
        delivery_ok = (
            delivery.get("passed") is True
            and int(delivery.get("alertCount", -1)) == len(alerts)
            and int(delivery.get("observedCount", -1)) == len(alerts)
        )
    except Exception:
        delivery, delivery_ok = {}, False
    if not delivery_ok:
        reasons.append("alert delivery drill did not route every discovered alert")

    document = {
        "schemaVersion": 1,
        "health": health,
        "requiredHealthComponents": component_status,
        "requiredMetrics": metric_status,
        "dashboards": dashboards,
        "alertRuleCount": len(alerts),
        "minimumAlertRuleCount": minimum_alerts,
        "alertDelivery": delivery,
        "passed": not reasons,
        "failureReasons": reasons,
    }
    pathlib.Path(args.output).write_text(json.dumps(document, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return 0 if document["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
