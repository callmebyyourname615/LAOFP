package com.example.switching.dispute.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.dispute.entity.DisputeEntity;
import com.example.switching.dispute.exception.DisputeNotFoundException;
import com.example.switching.dispute.repository.DisputeRepository;
import com.example.switching.liquidity.exception.InsufficientPoolBalanceException;
import com.example.switching.liquidity.service.PoolService;
import com.example.switching.webhook.service.WebhookEventPublisher;

/**
 * Executes the financial leg of a dispute refund:
 * hold funds from the responding PSP → update {@code refund_transactions} to
 * COMPLETED → confirm hold → fire webhook.
 *
 * <p>The refund reference is operational evidence only. It intentionally does
 * not create a reverse transfer/reversal transaction.
 */
@Service
public class DisputeAutoRefundService {

    private static final Logger log = LoggerFactory.getLogger(DisputeAutoRefundService.class);

    private final DisputeRepository     disputeRepo;
    private final JdbcTemplate          jdbcTemplate;
    private final PoolService           poolService;
    private final WebhookEventPublisher webhookPublisher;

    public DisputeAutoRefundService(DisputeRepository disputeRepo,
                                    JdbcTemplate jdbcTemplate,
                                    PoolService poolService,
                                    WebhookEventPublisher webhookPublisher) {
        this.disputeRepo      = disputeRepo;
        this.jdbcTemplate     = jdbcTemplate;
        this.poolService      = poolService;
        this.webhookPublisher = webhookPublisher;
    }

    /**
     * Initiate the financial refund for a dispute.  The dispute must already be in
     * a terminal status ({@code RESOLVED_REFUND}) before this is called.
     *
     * @param disputeId ID of the resolved dispute
     */
    @Transactional
    public RefundExecutionResult initiateRefund(Long disputeId) {
        DisputeEntity dispute = disputeRepo.findById(disputeId)
                .orElseThrow(() -> new DisputeNotFoundException(disputeId));

        // Load original transaction for amount + PSP roles
        Map<String, Object> txn;
        try {
            txn = jdbcTemplate.queryForMap(
                    "SELECT amount, source_bank, destination_bank FROM transactions WHERE transaction_ref = ? AND status = 'SETTLED' LIMIT 1",
                    dispute.getTxnRef());
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            log.warn("Original transaction missing for dispute {}: {}", disputeId, dispute.getTxnRef());
            throw new IllegalStateException("Original transaction not found: " + dispute.getTxnRef());
        }

        BigDecimal amount          = new BigDecimal(txn.get("amount").toString());
        String     respondingPspId = dispute.getRespondingPspId();  // pays refund
        String     raisingPspId   = dispute.getRaisingPspId();      // receives refund

        LocalDateTime now   = LocalDateTime.now();

        // 1. Insert refund_transactions INITIATED
        String refundRef = "DISP-HOLD-" + disputeId + "-" + System.nanoTime();
        Long refundId = jdbcTemplate.queryForObject(
                """
                INSERT INTO refund_transactions
                    (dispute_id, original_txn_ref, amount, status, initiated_at)
                VALUES (?, ?, ?, 'INITIATED', ?)
                RETURNING refund_id
                """,
                Long.class, disputeId, dispute.getTxnRef(), amount, now);

        try {
            RefundExecutionResult failedPrecheck = failIfInsufficientPool(
                    refundId,
                    disputeId,
                    dispute.getTxnRef(),
                    amount,
                    respondingPspId,
                    raisingPspId,
                    now);
            if (failedPrecheck != null) {
                return failedPrecheck;
            }

            // 2. Hold from responding PSP pool
            poolService.holdFunds(respondingPspId, refundRef, amount);

            // 3. Create a refund reference only. This is not a transfer/reversal.
            String refundTxnRef = "DRS-REFUND-" + disputeId + "-" + System.nanoTime();

            // 4. Update refund_transactions COMPLETED with the refund evidence ref
            jdbcTemplate.update(
                    "UPDATE refund_transactions SET status='COMPLETED', refund_txn_ref=?, completed_at=?, last_error=NULL WHERE refund_id=?",
                    refundTxnRef, now, refundId);

            // 5. Confirm pool hold
            poolService.confirmHold(refundRef);

            log.info("Dispute refund completed: disputeId={} refundTxnRef={} amount={}", disputeId, refundTxnRef, amount);

            // 6. Fire webhook to both PSPs
            Map<String, Object> payload = Map.of(
                    "disputeId",    disputeId,
                    "refundTxnRef", refundTxnRef,
                    "amount",       amount.toPlainString(),
                    "status",       "COMPLETED");
            webhookPublisher.publishQuietly("DISPUTE.REFUND.COMPLETED", raisingPspId,    refundTxnRef, payload);
            webhookPublisher.publishQuietly("DISPUTE.REFUND.COMPLETED", respondingPspId, refundTxnRef, payload);
            return new RefundExecutionResult(
                    refundId,
                    disputeId,
                    dispute.getTxnRef(),
                    refundTxnRef,
                    amount,
                    "COMPLETED",
                    now,
                    now,
                    respondingPspId,
                    raisingPspId,
                    null);
        } catch (RuntimeException ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            jdbcTemplate.update(
                    "UPDATE refund_transactions SET status='FAILED', last_error=?, completed_at=? WHERE refund_id=?",
                    message, LocalDateTime.now(), refundId);
            log.warn("Dispute refund failed: disputeId={} refundId={} amount={} error={}",
                    disputeId, refundId, amount, message);
            return new RefundExecutionResult(
                    refundId,
                    disputeId,
                    dispute.getTxnRef(),
                    null,
                    amount,
                    "FAILED",
                    now,
                    LocalDateTime.now(),
                    respondingPspId,
                    raisingPspId,
                    message);
        }
    }

    private RefundExecutionResult failIfInsufficientPool(
            Long refundId,
            Long disputeId,
            String txnRef,
            BigDecimal amount,
            String respondingPspId,
            String raisingPspId,
            LocalDateTime initiatedAt) {
        java.util.List<BigDecimal> availableRows = jdbcTemplate.queryForList(
                "SELECT available_balance FROM psp_pools WHERE psp_id = ?",
                BigDecimal.class,
                respondingPspId);
        if (availableRows.isEmpty()) {
            return null;
        }

        BigDecimal available = availableRows.get(0);
        if (available.compareTo(amount) >= 0) {
            return null;
        }

        String message = new InsufficientPoolBalanceException(respondingPspId, amount, available).getMessage();
        LocalDateTime failedAt = LocalDateTime.now();
        jdbcTemplate.update(
                "UPDATE refund_transactions SET status='FAILED', last_error=?, completed_at=? WHERE refund_id=?",
                message,
                failedAt,
                refundId);
        log.warn("Dispute refund precheck failed: disputeId={} refundId={} amount={} available={} psp={}",
                disputeId, refundId, amount, available, respondingPspId);
        return new RefundExecutionResult(
                refundId,
                disputeId,
                txnRef,
                null,
                amount,
                "FAILED",
                initiatedAt,
                failedAt,
                respondingPspId,
                raisingPspId,
                message);
    }

    public record RefundExecutionResult(
            Long refundId,
            Long disputeId,
            String originalTxnRef,
            String refundRef,
            BigDecimal amount,
            String status,
            LocalDateTime initiatedAt,
            LocalDateTime completedAt,
            String debitedPspId,
            String creditedPspId,
            String lastError
    ) {}
}
