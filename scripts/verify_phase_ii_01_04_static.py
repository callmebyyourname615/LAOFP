#!/usr/bin/env python3
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def require(path: str) -> str:
    target = ROOT / path
    if not target.is_file():
        raise AssertionError(f"missing required file: {path}")
    return target.read_text(encoding="utf-8")


def assert_contains(text: str, *needles: str) -> None:
    for needle in needles:
        if needle not in text:
            raise AssertionError(f"missing contract token: {needle}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--strict-predecessors", action="store_true")
    args = parser.parse_args()

    migration = require("src/main/resources/db/migration/V91__request_to_pay_foundation.sql")
    assert_contains(
        migration,
        "CREATE TABLE rtp_request",
        "CREATE TABLE rtp_authorisation",
        "CREATE TABLE rtp_installment_schedule",
        "CREATE TABLE rtp_state_transition",
        "request_fingerprint VARCHAR(64)",
        "UNIQUE (payee_participant_id, request_correlation_id)",
        "ON rtp_request(status, expires_at)",
    )
    if re.search(r"request_fingerprint\s+CHAR\s*\(", migration, re.I):
        raise AssertionError("RTP SHA-256 column must use VARCHAR(64), not CHAR")

    state_machine = require("src/main/java/com/example/switching/rtp/service/RtpStateMachine.java")
    assert_contains(
        state_machine,
        "PENDING_AUTH",
        "AUTHORISED",
        "PARTIALLY_SETTLED",
        "INSTALMENT_IN_PROGRESS",
        "SETTLED",
        "DECLINED",
        "EXPIRED",
        "CANCELLED",
    )

    service = require("src/main/java/com/example/switching/rtp/service/RtpRequestService.java")
    assert_contains(
        service,
        "ON CONFLICT (payee_participant_id, request_correlation_id) DO NOTHING",
        "RtpIdempotencyConflictException",
        "findByIdForUpdate",
        "assertCanRead",
        "recordTransition",
    )

    controller = require("src/main/java/com/example/switching/rtp/controller/RequestToPayController.java")
    assert_contains(controller, "@PostMapping", "@GetMapping", '"/{id}/cancel"')

    app = require("src/main/resources/application.yml")
    assert_contains(
        app,
        "PHASE_II_RTP_ENABLED:false",
        "RTP_DEFAULT_EXPIRY:24h",
        "RTP_MAXIMUM_EXPIRY:30d",
    )

    security = require("src/main/java/com/example/switching/security/config/SecurityConfig.java")
    assert_contains(
        security,
        'v1("/rtp/requests")',
        'v1("/rtp/requests/*")',
        'v1("/rtp/requests/*/cancel")',
    )

    for test in (
        "src/test/java/com/example/switching/rtp/RtpStateMachineTest.java",
        "src/test/java/com/example/switching/rtp/RtpRequestFingerprintTest.java",
        "src/test/java/com/example/switching/rtp/RequestToPayControllerTest.java",
        "src/test/java/com/example/switching/rtp/RtpRequestIntegrationTest.java",
    ):
        require(test)

    migration_names = {p.name for p in (ROOT / "src/main/resources/db/migration").glob("V*.sql")}
    missing = [version for version in (83, 84) if not any(name.startswith(f"V{version}__") for name in migration_names)]
    if missing:
        message = (
            "Phase II predecessor warning: source tree is missing migrations "
            + ", ".join(f"V{version}" for version in missing)
            + "; production certification must merge them before V85 rollout"
        )
        if args.strict_predecessors:
            raise AssertionError(message)
        print(f"WARNING: {message}")

    print("OK: Phase II-01 through II-04 static contract passed")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        raise SystemExit(1)
