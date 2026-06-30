package com.example.switching.outbox.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.switching.audit.service.AuditLogService;
import com.example.switching.idempotency.service.IdempotencyService;
import com.example.switching.liquidity.dto.PoolBalance;
import com.example.switching.liquidity.service.PoolService;
import com.example.switching.outbox.entity.OutboxEventEntity;
import com.example.switching.outbox.enums.OutboxStatus;
import com.example.switching.outbox.repository.OutboxEventRepository;
import com.example.switching.transfer.entity.TransferEntity;
import com.example.switching.transfer.enums.TransferStatus;
import com.example.switching.transfer.repository.TransferRepository;
import com.example.switching.transfer.service.PaymentFlowTracker;
import com.example.switching.transfer.service.TransferStateMachineService;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class OutboxRecoveryService {

    private static final String ENTITY_TYPE = "TRANSFER";
    private static final String SOURCE_SYSTEM = "WORKER";
    private static final String IDEMPOTENCY_CHANNEL = "API";

    private static final int DEFAULT_LIMIT = 50;

    private final int maxRetry;

    private static final String ERROR_CODE = "OUT-003";
    private static final String ERROR_MESSAGE = "Outbox event stuck in PROCESSING and reached retry limit";

    private final OutboxEventRepository outboxEventRepository;
    private final TransferRepository transferRepository;
    private final TransferStateMachineService transferStateMachineService;
    private final IdempotencyService idempotencyService;
    private final AuditLogService auditLogService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final PoolService poolService;
    private final PaymentFlowTracker flowTracker;

    public OutboxRecoveryService(OutboxEventRepository outboxEventRepository,
                                 TransferRepository transferRepository,
                                 TransferStateMachineService transferStateMachineService,
                                 IdempotencyService idempotencyService,
                                 AuditLogService auditLogService,
                                 JdbcTemplate jdbcTemplate,
                                 ObjectMapper objectMapper,
                                 PoolService poolService,
                                 PaymentFlowTracker flowTracker,
                                 @org.springframework.beans.factory.annotation.Value("${switching.outbox.worker.max-retry:3}") int maxRetry) {
        this.outboxEventRepository = outboxEventRepository;
        this.transferRepository = transferRepository;
        this.transferStateMachineService = transferStateMachineService;
        this.idempotencyService = idempotencyService;
        this.auditLogService = auditLogService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.poolService = poolService;
        this.flowTracker = flowTracker;
        this.maxRetry = maxRetry;
    }

    @Transactional
    public int recoverStuckProcessingEvents(int stuckAfterMinutes) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(stuckAfterMinutes);

        List<OutboxEventEntity> stuckEvents =
                outboxEventRepository.findStuckProcessingEvents(
                        OutboxStatus.PROCESSING.name(),
                        cutoff,
                        DEFAULT_LIMIT
                );

        int handledCount = 0;

        for (OutboxEventEntity event : stuckEvents) {
            int currentRetryCount = safeRetryCount(event.getRetryCount());
            int nextRetryCount = nextAttemptCount(currentRetryCount);

            if (nextRetryCount >= maxRetry) {
                boolean markedFailed = markAsTerminalFailed(event, stuckAfterMinutes, nextRetryCount);
                if (markedFailed) {
                    handledCount++;
                }
            } else {
                boolean recovered = recoverToPending(event, stuckAfterMinutes, nextRetryCount);
                if (recovered) {
                    handledCount++;
                }
            }
        }

        return handledCount;
    }

    private boolean recoverToPending(OutboxEventEntity event,
                                     int stuckAfterMinutes,
                                     int nextRetryCount) {
        int updated = outboxEventRepository.recoverProcessingEvent(
                event.getId(),
                OutboxStatus.PROCESSING.name(),
                OutboxStatus.PENDING.name(),
                nextRetryCount
        );

        if (updated != 1) {
            return false;
        }

        Map<String, Object> payload = basePayload(event, stuckAfterMinutes, nextRetryCount);
        payload.put("fromStatus", OutboxStatus.PROCESSING.name());
        payload.put("toStatus", OutboxStatus.PENDING.name());
        payload.put("reason", "PROCESSING_STUCK_TIMEOUT");
        payload.put("willRetry", true);

        auditLogService.log(
                "OUTBOX_STUCK_PROCESSING_RECOVERED",
                ENTITY_TYPE,
                event.getTransferRef(),
                SOURCE_SYSTEM,
                payload
        );

        return true;
    }

    private boolean markAsTerminalFailed(OutboxEventEntity event,
                                         int stuckAfterMinutes,
                                         int nextRetryCount) {
        int updated = outboxEventRepository.markProcessingEventAsFailed(
                event.getId(),
                OutboxStatus.PROCESSING.name(),
                OutboxStatus.FAILED.name(),
                nextRetryCount
        );

        if (updated != 1) {
            return false;
        }

        TransferEntity transfer = null;
        if (StringUtils.hasText(event.getTransferRef())) {
            transfer = transferRepository.findByTransferRef(event.getTransferRef()).orElse(null);
        }

        if (transfer != null) {
            markTransferProvisionalReadyForSettlement(transfer,
                    "Provisional settlement: outbox stuck in PROCESSING after retry exhaustion");
            PoolBalance confirmedPoolBalance = poolService.confirmHold(transfer.getTransferRef());
            transferRepository.save(transfer);

            if (StringUtils.hasText(transfer.getIdempotencyKey())) {
                idempotencyService.updateStatus(
                        IDEMPOTENCY_CHANNEL,
                        transfer.getIdempotencyKey(),
                        TransferStatus.READY_FOR_SETTLEMENT.name()
                );
            }
            flowTracker.markReadyForSettlement(transfer.getTransferRef(), transfer.getBusinessDate());

            Map<String, Object> provisionalPayload = basePayload(event, stuckAfterMinutes, nextRetryCount);
            provisionalPayload.put("reason", "PROCESSING_STUCK_MAX_RETRY_PROVISIONAL_SETTLEMENT");
            provisionalPayload.put("confirmationStatus", transfer.getConfirmationStatus());
            provisionalPayload.put("settlementConfidence", transfer.getSettlementConfidence());
            provisionalPayload.put("poolAvailableBalance", confirmedPoolBalance.availableBalance());
            provisionalPayload.put("poolHeldAmount", confirmedPoolBalance.heldAmount());
            auditLogService.log(
                    "TRANSFER_PROVISIONAL_READY_FOR_SETTLEMENT",
                    ENTITY_TYPE,
                    transfer.getTransferRef(),
                    SOURCE_SYSTEM,
                    provisionalPayload);
        }

        Map<String, Object> payload = basePayload(event, stuckAfterMinutes, nextRetryCount);
        payload.put("fromStatus", OutboxStatus.PROCESSING.name());
        payload.put("toStatus", OutboxStatus.FAILED.name());
        payload.put("reason", "PROCESSING_STUCK_maxRetry_EXCEEDED");
        payload.put("willRetry", false);
        payload.put("finalTransferStatus", TransferStatus.READY_FOR_SETTLEMENT.name());
        payload.put("settlementConfidence", "PROVISIONAL");

        auditLogService.log(
                "OUTBOX_STUCK_PROCESSING_TERMINAL_FAILED",
                ENTITY_TYPE,
                event.getTransferRef(),
                SOURCE_SYSTEM,
                payload
        );

        return true;
    }

    private void markTransferProvisionalReadyForSettlement(TransferEntity transfer, String reference) {
        if (isTerminalStatus(transfer.getStatus())) {
            return;
        }
        if (transfer.getStatus() != TransferStatus.READY_FOR_SETTLEMENT) {
            transferStateMachineService.transition(transfer, TransferStatus.READY_FOR_SETTLEMENT, null);
        }
        transfer.setConfirmationStatus("PROVISIONAL");
        transfer.setSettlementConfidence("PROVISIONAL");
        transfer.setReference(reference);
        transfer.setErrorCode(null);
        transfer.setErrorMessage(null);
    }

    private boolean isTerminalStatus(TransferStatus status) {
        return status == TransferStatus.REJECTED
                || status == TransferStatus.FAILED
                || status == TransferStatus.SETTLED
                || status == TransferStatus.SUCCESS
                || status == TransferStatus.REFUNDED;
    }

    private void openDrsDisputeIfAbsent(TransferEntity transfer,
                                        OutboxEventEntity event,
                                        int attemptNo) {
        try {
            Integer existing = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM disputes
                    WHERE txn_ref = ?
                      AND status IN ('OPEN', 'UNDER_REVIEW', 'ESCALATED')
                    """,
                    Integer.class,
                    transfer.getTransferRef());

            if (existing != null && existing > 0) {
                return;
            }

            LocalDateTime now = LocalDateTime.now();
            String evidence = objectMapper.writeValueAsString(List.of(
                    Map.of(
                            "reason", "OUTBOX_STUCK_PROCESSING_RETRY_EXHAUSTED",
                            "outboxEventId", event.getId(),
                            "errorCode", ERROR_CODE,
                            "attemptNo", attemptNo,
                            "maxRetry", maxRetry,
                            "lastError", ERROR_MESSAGE)));

            Long disputeId = jdbcTemplate.queryForObject(
                    """
                    INSERT INTO disputes
                        (txn_ref, raising_psp_id, responding_psp_id, dispute_type, status,
                         raised_at, sla_deadline, evidence, resolution_note, created_at, updated_at)
                    VALUES (?, ?, ?, 'TECHNICAL_ERROR', 'OPEN', ?, ?, ?, ?, ?, ?)
                    RETURNING dispute_id
                    """,
                    Long.class,
                    transfer.getTransferRef(),
                    transfer.getSourceBank(),
                    transfer.getDestinationBank(),
                    now,
                    now.plusDays(1),
                    evidence,
                    "Auto-opened by switching after stuck outbox processing retry exhaustion",
                    now,
                    now);

            auditLogService.log(
                    "DRS_DISPUTE_AUTO_OPENED",
                    ENTITY_TYPE,
                    transfer.getTransferRef(),
                    SOURCE_SYSTEM,
                    Map.of(
                            "disputeId", disputeId,
                            "transferRef", transfer.getTransferRef(),
                            "sourceBank", transfer.getSourceBank(),
                            "destinationBank", transfer.getDestinationBank(),
                            "reason", "OUTBOX_STUCK_PROCESSING_RETRY_EXHAUSTED"));
        } catch (Exception disputeEx) {
            auditLogService.log(
                    "DRS_DISPUTE_AUTO_OPEN_FAILED",
                    ENTITY_TYPE,
                    transfer.getTransferRef(),
                    SOURCE_SYSTEM,
                    Map.of(
                            "transferRef", transfer.getTransferRef(),
                            "reason", "OUTBOX_STUCK_PROCESSING_RETRY_EXHAUSTED",
                            "error", disputeEx.getMessage() == null ? "" : disputeEx.getMessage()));
        }
    }

    private Map<String, Object> basePayload(OutboxEventEntity event,
                                            int stuckAfterMinutes,
                                            int nextRetryCount) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("outboxEventId", event.getId());
        payload.put("transferRef", event.getTransferRef());
        payload.put("messageType", event.getMessageType());
        payload.put("stuckAfterMinutes", stuckAfterMinutes);
        payload.put("attemptNo", nextRetryCount);
        payload.put("maxRetry", maxRetry);
        payload.put("errorCode", ERROR_CODE);
        payload.put("category", "CORE");
        payload.put("layer", "OUTBOX");
        payload.put("phase", "DISPATCH_TRANSFER");
        payload.put("retryable", true);
        return payload;
    }

    private int safeRetryCount(Integer retryCount) {
        return retryCount == null ? 0 : retryCount;
    }

    private int nextAttemptCount(int currentRetryCount) {
        return Math.min(currentRetryCount + 1, maxRetry);
    }
}
