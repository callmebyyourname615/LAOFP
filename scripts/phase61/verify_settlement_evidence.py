#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
import json
import re
from pathlib import Path

PLACEHOLDER = re.compile(r"(?i)(replace|todo|tbd|change_me)")
ZERO_VALUES = {0, 0.0, "0", "0.0", "0.00"}


def text(value) -> bool:
    return isinstance(value, str) and bool(value.strip()) and not PLACEHOLDER.search(value)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--summary", type=Path, required=True)
    parser.add_argument("--reconciliation", type=Path, required=True)
    parser.add_argument("--attestation", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    summary = json.loads(args.summary.read_text(encoding="utf-8"))
    attestation = json.loads(args.attestation.read_text(encoding="utf-8"))
    errors: list[str] = []

    if summary.get("transactions") != 500000:
        errors.append("benchmark must contain exactly 500000 transactions")
    if summary.get("eligibleTransactionCount") != 500000:
        errors.append("eligibleTransactionCount must equal 500000")
    if summary.get("settlementItemCount") != 1000000:
        errors.append("settlementItemCount must equal two financial legs per transaction")
    if summary.get("positionTransactionLegCount") != 1000000:
        errors.append("position transaction-count total must equal two financial legs per transaction")
    if summary.get("passed") is not True:
        errors.append("benchmark summary is not PASS")
    for key in (
        "balanceMismatchCount", "duplicatePostingCount", "missingTransactionCount",
        "negativeCounterCount", "outboxUndeliveredCount",
    ):
        if int(summary.get(key, 1)) != 0:
            errors.append(f"{key} must be zero")
    if summary.get("balanceMismatchAmount") not in ZERO_VALUES:
        errors.append("balanceMismatchAmount must be exactly zero")

    if attestation.get("schemaVersion") != 1:
        errors.append("settlement attestation schemaVersion must equal 1")
    try:
        settlement_sla = float(attestation["settlementSlaSeconds"])
    except Exception:
        settlement_sla = 0
        errors.append("settlementSlaSeconds must be numeric and positive")
    if settlement_sla <= 0:
        errors.append("settlementSlaSeconds must be positive")
    if float(summary.get("durationSeconds", 10**12)) > settlement_sla:
        errors.append("settlement duration exceeds signed SLA")
    if attestation.get("debitEqualsCredit") is not True or attestation.get("reconciliationDifference") not in ZERO_VALUES:
        errors.append("financial reconciliation is not exactly balanced")
    for key in (
        "rebatchIdempotent", "retryIdempotent", "reversalAndRefundVerified",
        "participantFailureVerified", "outboxFullyDrained",
    ):
        if attestation.get(key) is not True:
            errors.append(f"{key} must be true")

    reconciliation_rows: list[dict] = []
    if not args.reconciliation.is_file() or args.reconciliation.stat().st_size == 0:
        errors.append("reconciliation evidence is empty")
    else:
        with args.reconciliation.open(encoding="utf-8", newline="") as stream:
            reconciliation_rows = list(csv.DictReader(stream))
        if not reconciliation_rows:
            errors.append("reconciliation evidence contains no checks")
        for row in reconciliation_rows:
            if str(row.get("passed", "")).lower() != "true":
                errors.append(f"reconciliation check failed: {row.get('check_name', 'unknown')}")
        names = {row.get("check_name") for row in reconciliation_rows}
        required = {"performance_transactions", "duplicate_transaction_refs", "failed_outbox_events", "audit_chain_missing_hash"}
        missing = sorted(required - names)
        if missing:
            errors.append(f"reconciliation checks missing: {missing}")

    for key in ("settlementLead", "financeControlLead", "signedAt"):
        if not text(attestation.get(key)):
            errors.append(f"{key} missing or placeholder")

    report = {
        "schemaVersion": 1,
        "passed": not errors,
        "summary": summary,
        "reconciliationChecks": reconciliation_rows,
        "attestation": attestation,
        "errors": errors,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"Phase 61H settlement certification: {'PASS' if not errors else 'FAIL'}")
    for error in errors:
        print("  ERROR:", error)
    return 0 if not errors else 1


if __name__ == "__main__":
    raise SystemExit(main())
