package com.example.switching.rtp.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.switching.rtp.dto.ConfirmRtpSettlementRequest;

@Component
@Profile("!migration")
@ConditionalOnProperty(
        prefix = "switching.phase-ii.rtp",
        name = "enabled",
        havingValue = "true")
public class RtpInstallmentScheduler {

    private static final int MAX_ATTEMPTS = 5;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final RtpSettlementGateway settlementGateway;
    private final RtpAuthorisationService authorisationService;
    private final Clock clock;

    public RtpInstallmentScheduler(
            JdbcTemplate jdbc,
            TransactionTemplate transactions,
            RtpSettlementGateway settlementGateway,
            RtpAuthorisationService authorisationService,
            @Qualifier("rtpClock") Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.settlementGateway = settlementGateway;
        this.authorisationService = authorisationService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${switching.phase-ii.rtp.installment-poll-ms:30000}")
    public void processDueInstallments() {
        for (Claim claim : claimDue()) {
            process(claim);
        }
    }

    List<Claim> claimDue() {
        List<Claim> claims = transactions.execute(status -> {
            OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT i.id AS installment_id,
                           i.request_id,
                           i.installment_number,
                           i.amount,
                           i.attempt_count,
                           r.payer_participant_id,
                           r.payee_participant_id,
                           r.payer_account,
                           r.payee_account,
                           r.currency,
                           r.settlement_inquiry_ref,
                           r.request_correlation_id
                      FROM rtp_installment_schedule i
                      JOIN rtp_request r ON r.id=i.request_id
                     WHERE r.status IN ('AUTHORISED','INSTALMENT_IN_PROGRESS')
                       AND (
                           (i.status='SCHEDULED' AND i.due_at<=?)
                           OR
                           (i.status='FAILED' AND i.next_attempt_at<=?)
                       )
                       AND i.attempt_count < ?
                     ORDER BY i.due_at, i.installment_number
                     LIMIT 100
                     FOR UPDATE OF i SKIP LOCKED
                    """, now, now, MAX_ATTEMPTS);
            List<Claim> claimed = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                UUID installmentId = (UUID) row.get("installment_id");
                int attempt = ((Number) row.get("attempt_count")).intValue() + 1;
                int changed = jdbc.update("""
                        UPDATE rtp_installment_schedule
                           SET status='PROCESSING',
                               attempt_count=?,
                               next_attempt_at=NULL,
                               last_error_code=NULL,
                               updated_at=now()
                         WHERE id=?
                           AND status IN ('SCHEDULED','FAILED')
                        """, attempt, installmentId);
                if (changed == 1) {
                    claimed.add(new Claim(
                            installmentId,
                            (UUID) row.get("request_id"),
                            ((Number) row.get("installment_number")).intValue(),
                            (BigDecimal) row.get("amount"),
                            attempt,
                            String.valueOf(row.get("payer_participant_id")),
                            String.valueOf(row.get("payee_participant_id")),
                            nullable(row.get("payer_account")),
                            String.valueOf(row.get("payee_account")),
                            String.valueOf(row.get("currency")),
                            nullable(row.get("settlement_inquiry_ref")),
                            String.valueOf(row.get("request_correlation_id"))));
                }
            }
            return List.copyOf(claimed);
        });
        return claims == null ? List.of() : claims;
    }

    void process(Claim claim) {
        try {
            if (claim.inquiryRef() == null || claim.inquiryRef().isBlank()) {
                throw new IllegalStateException("RTP installment inquiry reference is unavailable");
            }
            RtpSettlementGateway.SettlementSubmission submission = settlementGateway.submit(
                    new RtpSettlementGateway.SettlementCommand(
                            claim.payerParticipant(),
                            claim.payeeParticipant(),
                            claim.payerAccount(),
                            claim.payeeAccount(),
                            claim.amount(),
                            claim.currency(),
                            claim.inquiryRef(),
                            "RTP-" + claim.requestId() + "-INST-" + claim.installmentNumber(),
                            "RTP " + claim.correlationId() + " installment "
                                    + claim.installmentNumber()));
            transactions.executeWithoutResult(status -> jdbc.update("""
                    UPDATE rtp_installment_schedule
                       SET transaction_reference=?,
                           status=CASE WHEN ?='SETTLED' THEN 'PROCESSING' ELSE 'PROCESSING' END,
                           updated_at=now()
                     WHERE id=? AND status='PROCESSING'
                    """,
                    submission.transactionReference(),
                    submission.status(),
                    claim.installmentId()));
            if ("SETTLED".equalsIgnoreCase(submission.status())) {
                authorisationService.confirmSettlement(
                        claim.requestId(),
                        new ConfirmRtpSettlementRequest(
                                submission.transactionReference(),
                                claim.amount(),
                                claim.installmentNumber()),
                        new RtpActor("rtp-installment-scheduler", null, true));
            }
        } catch (RuntimeException exception) {
            markFailure(claim, exception.getClass().getSimpleName());
        }
    }

    private void markFailure(Claim claim, String errorCode) {
        long delaySeconds = Math.min(3600L, 30L << Math.min(6, claim.attempt() - 1));
        Instant retryAt = clock.instant().plusSeconds(delaySeconds);
        transactions.executeWithoutResult(status -> jdbc.update("""
                UPDATE rtp_installment_schedule
                   SET status='FAILED',
                       next_attempt_at=CASE WHEN attempt_count < ? THEN ? ELSE NULL END,
                       last_error_code=?,
                       updated_at=now()
                 WHERE id=? AND status='PROCESSING'
                """,
                MAX_ATTEMPTS,
                OffsetDateTime.ofInstant(retryAt, ZoneOffset.UTC),
                errorCode,
                claim.installmentId()));
    }

    private static String nullable(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    record Claim(
            UUID installmentId,
            UUID requestId,
            int installmentNumber,
            BigDecimal amount,
            int attempt,
            String payerParticipant,
            String payeeParticipant,
            String payerAccount,
            String payeeAccount,
            String currency,
            String inquiryRef,
            String correlationId) {}
}
