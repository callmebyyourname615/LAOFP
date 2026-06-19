package com.example.switching.outbox.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.switching.audit.service.AuditLogService;
import com.example.switching.idempotency.service.IdempotencyService;
import com.example.switching.outbox.entity.OutboxEventEntity;
import com.example.switching.outbox.enums.OutboxStatus;
import com.example.switching.outbox.repository.OutboxEventRepository;
import com.example.switching.transfer.entity.TransferEntity;
import com.example.switching.transfer.enums.TransferStatus;
import com.example.switching.transfer.repository.TransferRepository;
import com.example.switching.transfer.service.TransferStateMachineService;

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

    public OutboxRecoveryService(OutboxEventRepository outboxEventRepository,
                                 TransferRepository transferRepository,
                                 TransferStateMachineService transferStateMachineService,
                                 IdempotencyService idempotencyService,
                                 AuditLogService auditLogService,
                                 @org.springframework.beans.factory.annotation.Value("${switching.outbox.worker.max-retry:3}") int maxRetry) {
        this.outboxEventRepository = outboxEventRepository;
        this.transferRepository = transferRepository;
        this.transferStateMachineService = transferStateMachineService;
        this.idempotencyService = idempotencyService;
        this.auditLogService = auditLogService;
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
            int nextRetryCount = currentRetryCount + 1;

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
                OutboxStatus.PENDING.name()
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
                OutboxStatus.FAILED.name()
        );

        if (updated != 1) {
            return false;
        }

        TransferEntity transfer = null;
        if (StringUtils.hasText(event.getTransferRef())) {
            transfer = transferRepository.findByTransferRef(event.getTransferRef()).orElse(null);
        }

        if (transfer != null) {
            transferStateMachineService.transition(transfer, TransferStatus.REJECTED, ERROR_CODE);
            transfer.setErrorCode(ERROR_CODE);
            transfer.setErrorMessage(ERROR_MESSAGE);
            transferRepository.save(transfer);

            if (StringUtils.hasText(transfer.getIdempotencyKey())) {
                idempotencyService.updateStatus(
                        IDEMPOTENCY_CHANNEL,
                        transfer.getIdempotencyKey(),
                        TransferStatus.REJECTED.name()
                );
            }
        }

        Map<String, Object> payload = basePayload(event, stuckAfterMinutes, nextRetryCount);
        payload.put("fromStatus", OutboxStatus.PROCESSING.name());
        payload.put("toStatus", OutboxStatus.FAILED.name());
        payload.put("reason", "PROCESSING_STUCK_maxRetry_EXCEEDED");
        payload.put("willRetry", false);
        payload.put("finalTransferStatus", TransferStatus.REJECTED.name());

        auditLogService.log(
                "OUTBOX_STUCK_PROCESSING_TERMINAL_FAILED",
                ENTITY_TYPE,
                event.getTransferRef(),
                SOURCE_SYSTEM,
                payload
        );

        return true;
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
}
