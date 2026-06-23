#!/usr/bin/env python3
"""Static contract verifier for Phase 70A-70J."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

REQUIRED = [
    "AGENT/PHASE_70A_70J_IMPLEMENTATION_CHECKLIST.md",
    "config/phase70-participant-traffic-policy.yaml",
    "src/main/resources/phase70/participant-traffic-policy.json",
    "src/main/java/com/example/switching/crossborder/jdbc/PostgresTemporalBinder.java",
    "src/main/java/com/example/switching/traffic/ratelimit/ParticipantRateLimitPolicyService.java",
    "src/main/java/com/example/switching/traffic/ratelimit/ParticipantTokenBucketService.java",
    "src/test/java/com/example/switching/traffic/ratelimit/ParticipantRateLimitPolicyServiceTest.java",
    "src/main/java/com/example/switching/consistency/ReplicaFreshnessProbe.java",
    "src/main/java/com/example/switching/consistency/ConsistencyAwareReportingJdbcOperations.java",
    "src/main/java/com/example/switching/promotion/reconciliation/PromotionFunderLedgerReconciliationService.java",
    "src/main/java/com/example/switching/promotion/reconciliation/PromotionFunderLedgerReconciliationController.java",
    "scripts/phase70/run_phase70.sh",
    "schemas/phase70/phase70-result.schema.json",
    ".github/workflows/phase70-traffic-financial-safety.yml",
]

PROTECTED_PREFIXES = (
    "scripts/phase65/",
    "scripts/phase66/",
    "scripts/phase67/",
    "scripts/phase68/",
    "scripts/phase69/",
)


def read(root: Path, relative: str) -> str:
    return (root / relative).read_text(encoding="utf-8")


def verify(root: Path) -> dict:
    errors: list[str] = []
    for relative in REQUIRED:
        if not (root / relative).is_file():
            errors.append(f"missing required file: {relative}")

    try:
        policy = json.loads(read(root, "config/phase70-participant-traffic-policy.yaml"))
        if not policy.get("version"):
            errors.append("rate-limit policy version missing")
        default = policy.get("defaultQuota") or {}
        if int(default.get("capacity", 0)) < 1:
            errors.append("default quota capacity must be positive")
    except Exception as exc:  # noqa: BLE001
        errors.append(f"invalid phase70 rate-limit policy: {exc}")

    checks = {
        "webhook fallback mapper": (
            "src/main/java/com/example/switching/webhook/crypto/WebhookEncryptionConfiguration.java",
            ["ObjectProvider<ObjectMapper>", "fallbackObjectMapper"],
        ),
        "explicit timestamptz binding": (
            "src/main/java/com/example/switching/crossborder/jdbc/PostgresTemporalBinder.java",
            ["Types.TIMESTAMP_WITH_TIMEZONE", "setObject(index, instant"],
        ),
        "rail journal binder integration": (
            "src/main/java/com/example/switching/crossborder/service/RailMessageJournalService.java",
            ["PostgresTemporalBinder.setInstant"],
        ),
        "retry-after response": (
            "src/main/java/com/example/switching/security/filter/RateLimitFilter.java",
            ["Retry-After", "PARTICIPANT_RATE_LIMIT_EXCEEDED", "REQ-004"],
        ),
        "runtime policy reload": (
            "src/main/java/com/example/switching/traffic/ratelimit/ParticipantRateLimitPolicyService.java",
            ["@Scheduled", "retaining last good policy", "reloadNow"],
        ),
        "promotion reconciliation endpoint": (
            "src/main/java/com/example/switching/promotion/reconciliation/PromotionFunderLedgerReconciliationController.java",
            ["/api/operations/promotions/funder-ledger", "/reconciliation"],
        ),
        "replica fail-closed": (
            "src/main/java/com/example/switching/consistency/ConsistencyAwareReportingJdbcOperations.java",
            ["EVENTUAL", "return primary"],
        ),
    }
    for name, (relative, needles) in checks.items():
        path = root / relative
        if not path.is_file():
            continue
        text = path.read_text(encoding="utf-8")
        for needle in needles:
            if needle not in text:
                errors.append(f"{name}: missing {needle!r} in {relative}")

    operations_test = root / "src/test/java/com/example/switching/operations/service/OperationsGenerateRoutesForBankIntegrationTest.java"
    if operations_test.is_file():
        text = operations_test.read_text(encoding="utf-8")
        if "DELETE FROM participants" in text:
            errors.append("70B regression: route-generation test still deletes participants")
        service = read(root, "src/main/java/com/example/switching/operations/service/OperationsGenerateRoutesForBankService.java")
        if "AND EXISTS" not in service or "cc.enabled = TRUE" not in service:
            errors.append("70B routable-participant candidate filter missing")

    migration_dir = root / "src/main/resources/db/migration"
    phase70_migrations = [p.name for p in migration_dir.glob("V*__*phase70*")]
    if phase70_migrations:
        errors.append(f"unexpected Phase 70 migration(s): {phase70_migrations}")

    result = {
        "phase": "70A-70J",
        "status": "PASS" if not errors else "FAIL",
        "requiredFiles": len(REQUIRED),
        "errors": errors,
    }
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=".")
    parser.add_argument("--json-output")
    args = parser.parse_args()
    result = verify(Path(args.root).resolve())
    payload = json.dumps(result, indent=2, sort_keys=True)
    print(payload)
    if args.json_output:
        Path(args.json_output).write_text(payload + "\n", encoding="utf-8")
    return 0 if result["status"] == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
