#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
checks = {
    "src/main/java/com/example/switching/dashboard/settlement/controller/SettlementDashboardController.java": [
        '/api/dashboard/settlement', "PERM_DASHBOARD_SETTLEMENT"],
    "src/main/java/com/example/switching/dashboard/risk/controller/RiskDashboardController.java": [
        '/api/dashboard/risk', "PERM_DASHBOARD_RISK"],
    "src/main/java/com/example/switching/dashboard/crossborder/controller/CrossBorderDashboardController.java": [
        '/api/dashboard/crossborder', "PERM_DASHBOARD_CROSSBORDER"],
    "src/main/java/com/example/switching/dashboard/settlement/service/SettlementDashboardService.java": [
        "settlement_cycles", "settlement_positions", "smos_maker_checker_requests"],
    "src/main/java/com/example/switching/dashboard/risk/service/RiskDashboardService.java": [
        "fraud_scores", "fraud_velocity_decision", "sanctions_screening_results"],
    "src/main/java/com/example/switching/dashboard/crossborder/service/CrossBorderDashboardService.java": [
        "cross_border_rail_message", "crossborder_transfers", "fx_corridors", "cross_border_rail_reconciliation"],
    "src/test/java/com/example/switching/dashboard/CriticalDashboardIntegrationTest.java": [
        "settlementDashboardReturns", "riskDashboardReturns", "crossBorderDashboardIncludes"],
}
failures = []
for relative, needles in checks.items():
    path = ROOT / relative
    if not path.is_file():
        failures.append(f"missing file: {relative}")
        continue
    text = path.read_text(encoding="utf-8")
    for needle in needles:
        if needle not in text:
            failures.append(f"{relative}: missing {needle!r}")

migration = (ROOT / "src/main/resources/db/migration/V97__smos_user_access_management.sql").read_text(encoding="utf-8")
for permission in ("dashboard','settlement", "dashboard','risk", "dashboard','crossborder"):
    if permission not in migration:
        failures.append(f"V97 missing dashboard permission {permission}")

if failures:
    for failure in failures:
        print(f"FAIL: {failure}")
    print(f"Critical dashboard static contract: FAIL ({len(failures)} issue(s))")
    sys.exit(1)
print("Critical dashboard static contract: PASS")
