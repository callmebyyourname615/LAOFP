#!/usr/bin/env python3
"""Fail-closed repository contract for Phase II-05 through Phase II-24."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    candidate = ROOT / path
    if not candidate.is_file():
        raise AssertionError(f"missing {path}")
    return candidate.read_text(encoding="utf-8")


def contains(path: str, *tokens: str) -> str:
    text = read(path)
    for token in tokens:
        if token not in text:
            raise AssertionError(f"{path} missing token: {token}")
    return text


def require_tests(*names: str) -> None:
    for name in names:
        read(f"src/test/java/com/example/switching/phaseii/{name}")


def main() -> int:
    migrations = {
        86: "promotion_management.sql",
        87: "push_payment_orchestrator.sql",
        88: "scheduled_report_delivery.sql",
        89: "cross_border_rail_journal.sql",
        90: "rtp_authorisation_settlement_extensions.sql",
    }
    migration_text = {
        version: read(f"src/main/resources/db/migration/V{version}__{name}")
        for version, name in migrations.items()
    }
    for version, text in migration_text.items():
        if re.search(r"sha256\s+CHAR\s*\(", text, re.IGNORECASE):
            raise AssertionError(f"V{version} introduces CHAR SHA-256")
        if "DROP TABLE" in text.upper() or "DROP COLUMN" in text.upper():
            raise AssertionError(f"V{version} contains destructive DDL")

    contains(
        "src/main/resources/db/migration/V96__rtp_authorisation_settlement_extensions.sql",
        "settlement_inquiry_ref",
        "attempt_count INTEGER NOT NULL DEFAULT 0",
        "next_attempt_at TIMESTAMPTZ",
        "result_payload JSONB",
        "request_sha256 VARCHAR(64)",
    )

    contains(
        "src/main/java/com/example/switching/rtp/service/RtpAuthorisationService.java",
        "FULL mode",
        "Installment total",
        "Settlement exceeds authorised amount",
        'events.publish("rtp.authorised"',
        'events.publish("rtp.settled"',
    )
    contains(
        "src/main/java/com/example/switching/rtp/service/RtpExpiryScheduler.java",
        "FOR UPDATE SKIP LOCKED",
        'events.publish("rtp.expired"',
    )
    contains(
        "src/main/java/com/example/switching/rtp/service/RtpInstallmentScheduler.java",
        "FOR UPDATE OF i SKIP LOCKED",
        "settlement_inquiry_ref",
        "next_attempt_at",
        "attempt_count <",
    )

    contains(
        "src/main/java/com/example/switching/promotion/service/PromotionEligibilityEvaluator.java",
        "Unsupported promotion rule field/operator",
        "BETWEEN",
    )
    contains(
        "src/main/java/com/example/switching/promotion/service/PromotionBudgetService.java",
        "budget_cap-budget_reserved-budget_consumed>=?",
        "budget_reserved>=?",
    )
    contains(
        "src/main/java/com/example/switching/promotion/service/PromotionApplicationService.java",
        "reservation",
        "release",
        "promotion.applied",
    )
    contains(
        "src/main/java/com/example/switching/promotion/service/PromotionSettlementService.java",
        "consume",
        "release",
        "promotion.settled",
    )
    contains(
        "src/main/java/com/example/switching/fees/FeeAssessmentResult.java",
        "grossFee",
        "promotionDiscount",
        "netFee",
        "promotions",
    )

    contains(
        "src/main/java/com/example/switching/paymentorchestration/PushPaymentOrchestrator.java",
        "Idempotency key reused with different payload",
        "request_payload",
        "result_payload",
        "policies.byId",
        "pg_advisory_xact_lock",
        "push_payment_transition",
    )
    contains(
        "src/main/java/com/example/switching/paymentorchestration/PushPaymentRetryScheduler.java",
        "next_attempt_at<=now()",
    )
    contains(
        "src/main/java/com/example/switching/transfer/service/TransferSubmissionService.java",
        "push-payment-orchestrator.enabled",
    )
    contains(
        "src/main/java/com/example/switching/qr/service/QrPaymentSubmissionService.java",
        "push-payment-orchestrator.enabled",
    )
    contains(
        "src/main/java/com/example/switching/billpayment/service/BillPaymentSubmissionService.java",
        "push-payment-orchestrator.enabled",
    )

    contains(
        "src/main/java/com/example/switching/crossborder/adapter/AbstractJsonRailAdapter.java",
        "mTLS requires JVM keyStore and trustStore",
        "X-Signature",
    )
    contains(
        "src/main/java/com/example/switching/crossborder/adapter/UpiInboundRailAdapter.java",
        "UPI outward flow is disabled",
    )
    contains(
        "src/main/java/com/example/switching/crossborder/controller/CrossBorderInboundController.java",
        '@RequestHeader("X-Partner-Key")',
        "MessageDigest.isEqual",
        "MAX_PAYLOAD_BYTES",
    )
    contains(
        "src/main/java/com/example/switching/crossborder/service/RailComplianceDispatchService.java",
        "screening == null || !screening.isClear()",
        "cross_border.compliance_blocked",
    )
    contains(
        "src/main/java/com/example/switching/crossborder/service/RailMessageJournalService.java",
        "request_sha256",
        "Rail external reference was replayed with different content",
    )
    contains(
        "src/main/java/com/example/switching/crossborder/service/CrossBorderReconciliationService.java",
        "@Transactional(isolation = Isolation.REPEATABLE_READ)",
        "MISSING_EXTERNAL",
        "MISSING_INTERNAL",
    )
    contains(
        "src/main/java/com/example/switching/crossborder/service/RailHttpTransport.java",
        "Rail endpoint must be HTTPS; HTTP is allowed only for loopback tests",
        "HttpClient.Redirect.NEVER",
    )

    contains(
        "src/main/java/com/example/switching/reportdelivery/SftpDeliveryService.java",
        "StrictHostKeyChecking=yes",
        "rename ",
    )
    contains(
        "src/main/java/com/example/switching/reportdelivery/S3DeliveryService.java",
        "S3 endpoint must use HTTPS; HTTP is allowed only for loopback tests",
    )
    contains(
        "src/main/java/com/example/switching/reportdelivery/EmailLinkDeliveryService.java",
        "Report download base URL must use HTTPS; HTTP is allowed only for loopback tests",
        "LINK_TTL_SECONDS = 86_400L",
    )
    contains(
        "src/main/java/com/example/switching/reportdelivery/ReportDestinationResolver.java",
        "must be an env:NAME reference",
    )
    contains(
        "src/main/java/com/example/switching/reportdelivery/ReportDeliveryService.java",
        "FOR UPDATE OF r SKIP LOCKED",
        "recoverStaleClaims",
        "STALE_DELIVERY_CLAIM",
        '"report.delivered"',
        '"report.delivery_failed"',
    )
    contains(
        "src/main/java/com/example/switching/reportdelivery/ReportArtifactService.java",
        "ON CONFLICT(report_type, recipient_participant_id, generation_key)",
        "DO NOTHING",
    )
    contains(
        "src/main/java/com/example/switching/observability/PhaseIIOperationalMetrics.java",
        "switching.phase2.rtp.pending",
        "switching.phase2.report.delivery.backlog",
    )

    application = read("src/main/resources/application.yml")
    for flag in (
        "PHASE_II_RTP_ENABLED:false",
        "PHASE_II_PROMOTION_ENABLED:false",
        "PHASE_II_PUSH_ORCHESTRATOR_ENABLED:false",
        "PHASE_II_CROSS_BORDER_ENABLED:false",
        "PHASE_II_REPORT_DELIVERY_ENABLED:false",
    ):
        if flag not in application:
            raise AssertionError(f"feature flag is not default-off: {flag}")

    require_tests(
        "PromotionEligibilityEvaluatorTest.java",
        "RailSignatureVerifierTest.java",
        "ReportLinkSigningTest.java",
        "PhaseII0524MigrationIntegrationTest.java",
        "PromotionBudgetConcurrencyIntegrationTest.java",
        "PushPaymentOrchestratorIntegrationTest.java",
        "RailMessageJournalIntegrationTest.java",
        "ReportArtifactIdempotencyIntegrationTest.java",
        "RtpAuthorisationIntegrationTest.java",
    )

    security = read("src/main/java/com/example/switching/security/config/SecurityConfig.java")
    for route in (
        "/authorise",
        "/decline",
        "/settlements",
        "/v1/promotions/**",
        "/v1/operator/report-delivery-schedules/**",
    ):
        if route not in security:
            raise AssertionError(f"security route missing: {route}")

    for path in (
        "PHASE_II_05_24_PLAN.md",
        "docs/api/phase-ii-services.md",
        "docs/runbooks/PHASE_II_RTP_OPERATIONS.md",
        "docs/runbooks/PHASE_II_PROMOTION_OPERATIONS.md",
        "docs/runbooks/PHASE_II_CROSS_BORDER_ADAPTERS.md",
        "docs/runbooks/PHASE_II_REPORT_DELIVERY.md",
        ".github/workflows/phase-ii-05-24-contract.yml",
        "scripts/curl_phase_ii_05_24_tests.sh",
    ):
        read(path)

    print("OK: Phase II-05 through II-24 static contract passed")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError as error:
        print(f"FAIL: {error}", file=sys.stderr)
        raise SystemExit(1)
