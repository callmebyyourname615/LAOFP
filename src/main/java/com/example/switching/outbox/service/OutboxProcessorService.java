package com.example.switching.outbox.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.example.switching.audit.service.AuditLogService;
import com.example.switching.common.error.ErrorCatalog;
import com.example.switching.common.error.ErrorClassifier;
import com.example.switching.config.FpreProperties;
import com.example.switching.idempotency.service.IdempotencyService;
import com.example.switching.liquidity.dto.PoolBalance;
import com.example.switching.liquidity.service.PoolService;
import com.example.switching.outbox.dto.BankDispatchResult;
import com.example.switching.outbox.dto.DispatchTransferCommand;
import com.example.switching.outbox.entity.OutboxEventEntity;
import com.example.switching.outbox.enums.FailureClass;
import com.example.switching.outbox.enums.OutboxStatus;
import com.example.switching.outbox.repository.OutboxEventRepository;
import com.example.switching.settlement.service.HighValueRtgsInstructionService;
import com.example.switching.transfer.entity.TransferEntity;
import com.example.switching.transfer.enums.TransferStatus;
import com.example.switching.transfer.repository.TransferRepository;
import com.example.switching.transfer.service.PaymentFlowTracker;
import com.example.switching.transfer.service.TransactionEventPublisher;
import com.example.switching.webhook.service.WebhookEventPublisher;
import com.example.switching.transfer.service.TransferStateMachineService;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Service
public class OutboxProcessorService {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessorService.class);

    private static final String ENTITY_TYPE = "TRANSFER";
    private static final String SOURCE_SYSTEM = "WORKER";
    private static final String IDEMPOTENCY_CHANNEL = "API";
    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;

    private static final String OUTBOX_ATTEMPT_SQL = """
            INSERT INTO outbox_attempts
                (outbox_message_id, attempt_number, status, error_code, error_message,
                 failure_class, connector_name, duration_ms)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final int maxRetry;

    private final OutboxEventRepository outboxEventRepository;
    private final TransferRepository transferRepository;
    private final TransferStateMachineService transferStateMachineService;
    private final OutboxIsoMessageDispatchService outboxIsoMessageDispatchService;
    private final OutboxFailureClassificationService failureClassificationService;
    private final OutboxRetryScheduleService retryScheduleService;
    private final OutboxAmbiguousCheckService ambiguousCheckService;
    private final OutboxAutoReversalService autoReversalService;
    private final PspAutoSuspensionService pspSuspensionService;
    private final FpreProperties fpre;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionEventPublisher eventPublisher;
    private final PaymentFlowTracker        flowTracker;
    private final PoolService               poolService;
    private final HighValueRtgsInstructionService highValueRtgsInstructionService;

    private final AuditLogService auditLogService;
    private final IdempotencyService idempotencyService;
    private final ErrorClassifier errorClassifier;
    private final WebhookEventPublisher webhookPublisher;
    private final TransactionTemplate transactionTemplate;
    private final Counter dispatchSuccessCounter;
    private final Counter dispatchBusinessFailureCounter;
    private final Counter dispatchTechnicalFailureCounter;
    private final Timer dispatchTimer;

    public OutboxProcessorService(OutboxEventRepository outboxEventRepository,
            TransferRepository transferRepository,
            TransferStateMachineService transferStateMachineService,
            OutboxIsoMessageDispatchService outboxIsoMessageDispatchService,
            OutboxFailureClassificationService failureClassificationService,
            OutboxRetryScheduleService retryScheduleService,
            OutboxAmbiguousCheckService ambiguousCheckService,
            OutboxAutoReversalService autoReversalService,
            PspAutoSuspensionService pspSuspensionService,
            FpreProperties fpre,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate,
            AuditLogService auditLogService,
            IdempotencyService idempotencyService,
            ErrorClassifier errorClassifier,
            TransactionEventPublisher eventPublisher,
            PaymentFlowTracker flowTracker,
            PoolService poolService,
            HighValueRtgsInstructionService highValueRtgsInstructionService,
            WebhookEventPublisher webhookPublisher,
            PlatformTransactionManager transactionManager,
            MeterRegistry meterRegistry) {
        this.outboxEventRepository = outboxEventRepository;
        this.transferRepository = transferRepository;
        this.transferStateMachineService = transferStateMachineService;
        this.outboxIsoMessageDispatchService = outboxIsoMessageDispatchService;
        this.failureClassificationService = failureClassificationService;
        this.retryScheduleService = retryScheduleService;
        this.ambiguousCheckService = ambiguousCheckService;
        this.autoReversalService   = autoReversalService;
        this.pspSuspensionService  = pspSuspensionService;
        this.fpre      = fpre;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.auditLogService = auditLogService;
        this.idempotencyService = idempotencyService;
        this.errorClassifier = errorClassifier;
        this.eventPublisher    = eventPublisher;
        this.flowTracker       = flowTracker;
        this.poolService       = poolService;
        this.highValueRtgsInstructionService = highValueRtgsInstructionService;
        this.webhookPublisher  = webhookPublisher;
        this.maxRetry = fpre.getRetryAttempts();
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.dispatchSuccessCounter = Counter.builder("payment.outbox.dispatch.success")
                .description("Outbox events dispatched successfully")
                .register(meterRegistry);
        this.dispatchBusinessFailureCounter = Counter.builder("payment.outbox.dispatch.failed")
                .tag("type", "business")
                .description("Outbox events rejected by downstream bank")
                .register(meterRegistry);
        this.dispatchTechnicalFailureCounter = Counter.builder("payment.outbox.dispatch.failed")
                .tag("type", "technical")
                .description("Outbox events failed due to technical errors (terminal)")
                .register(meterRegistry);
        this.dispatchTimer = Timer.builder("payment.outbox.dispatch.duration")
                .description("Time taken to process a single outbox event")
                .register(meterRegistry);
    }

    public void processSingleEvent(Long outboxEventId) {
        MDC.put("outboxEventId", String.valueOf(outboxEventId));
        Timer.Sample timerSample = Timer.start();
        try {
            doProcessSingleEvent(outboxEventId);
        } finally {
            timerSample.stop(dispatchTimer);
            MDC.remove("outboxEventId");
            MDC.remove("transferRef");
        }
    }

    private void doProcessSingleEvent(Long outboxEventId) {
        OutboxEventEntity claimedEvent = transactionTemplate.execute(status -> claimEvent(outboxEventId));

        if (claimedEvent == null) {
            log.debug("Skip outboxEventId={} because it was already claimed or is no longer pending", outboxEventId);
            return;
        }

        DispatchTransferCommand command = null;

        try {
            command = objectMapper.readValue(claimedEvent.getPayload(), DispatchTransferCommand.class);
            MDC.put("transferRef", command.getTransferRef());

            Map<String, Object> isoDispatchPayload = new LinkedHashMap<>();
            isoDispatchPayload.put("outboxEventId", claimedEvent.getId());
            isoDispatchPayload.put("transferRef", command.getTransferRef());
            isoDispatchPayload.put("isoMessageId", command.getIsoMessageId());
            isoDispatchPayload.put("messageType", claimedEvent.getMessageType());
            isoDispatchPayload.put("connectorName", command.getConnectorName());
            isoDispatchPayload.put("routeCode", command.getRouteCode());

            auditLogService.log(
                    "OUTBOX_ISO_MESSAGE_RESOLVED",
                    ENTITY_TYPE,
                    command.getTransferRef(),
                    SOURCE_SYSTEM,
                    isoDispatchPayload);
            if (command.getIsoMessageId() == null) {
                throw new IllegalStateException(
                        "Missing isoMessageId in outbox payload for transferRef: " + command.getTransferRef());
            }

            // Publish DISPATCHED event just before actual network call
            eventPublisher.publishQuietly(command.getTransferRef(), "TRANSFER_DISPATCHED",
                    java.time.LocalDate.now(),
                    java.util.Map.of("outboxEventId", claimedEvent.getId(),
                            "connectorName", String.valueOf(command.getConnectorName()),
                            "routeCode",     String.valueOf(command.getRouteCode())),
                    SOURCE_SYSTEM);

            BankDispatchResult result = outboxIsoMessageDispatchService
                    .dispatchEncryptedIsoMessage(claimedEvent.getPayload());
            if (result == null) {
                throw new IllegalStateException("OutboxIsoMessageDispatchService returned null result");
            }

            final DispatchTransferCommand finalCommand = command;
            final BankDispatchResult finalResult = result;

            final String finalConnectorName = command.getConnectorName();
            if (result.success()) {
                transactionTemplate.executeWithoutResult(
                        status -> finalizeSuccess(claimedEvent.getId(), finalCommand.getTransferRef(),
                                finalResult, finalConnectorName));
            } else {
                transactionTemplate.executeWithoutResult(status -> finalizeBusinessFailure(claimedEvent.getId(),
                        finalCommand.getTransferRef(), finalResult, finalConnectorName));
            }

        } catch (Exception ex) {
            final String transferRef = resolveTransferRef(claimedEvent, command);

            transactionTemplate
                    .executeWithoutResult(status -> finalizeTechnicalFailure(claimedEvent.getId(), transferRef, ex));
        }
    }

    private OutboxEventEntity claimEvent(Long outboxEventId) {
        int updated = outboxEventRepository.claimPendingEvent(
                outboxEventId,
                OutboxStatus.PENDING,
                OutboxStatus.PROCESSING);

        if (updated == 0) {
            return null;
        }

        OutboxEventEntity event = getOutboxEventOrThrow(outboxEventId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("outboxEventId", event.getId());
        payload.put("transferRef", event.getTransferRef());
        payload.put("messageType", event.getMessageType());

        auditLogService.log(
                "OUTBOX_DISPATCH_STARTED",
                ENTITY_TYPE,
                event.getTransferRef(),
                SOURCE_SYSTEM,
                payload);

        log.info("Claimed outboxEventId={} transferRef={} as PROCESSING",
                event.getId(), event.getTransferRef());

        return event;
    }

    private void finalizeSuccess(Long outboxEventId,
            String transferRef,
            BankDispatchResult result,
            String connectorName) {
        OutboxEventEntity event = getOutboxEventOrThrow(outboxEventId);
        TransferEntity transfer = getTransferOrThrow(transferRef);

        PoolBalance confirmedPoolBalance = poolService.confirmHold(transferRef);

        transferStateMachineService.transition(transfer, TransferStatus.READY_FOR_SETTLEMENT, null);
        transfer.setExternalReference(result.getExternalReference());
        transfer.setReference(result.getReference());
        transfer.setErrorCode(null);
        transfer.setErrorMessage(null);
        transferRepository.save(transfer);
        if (transfer.isHighValue() && "RTGS".equalsIgnoreCase(transfer.getSettlementMethod())) {
            highValueRtgsInstructionService.generatePendingInstruction(transfer.getTransferRef());
        }

        event.setStatus(OutboxStatus.SUCCESS);
        event.setLastError(null);
        event.setFailureClass(null);
        event.setWillRetry(false);
        event.setNextRetryAt(null);
        outboxEventRepository.save(event);

        if (StringUtils.hasText(transfer.getIdempotencyKey())) {
            idempotencyService.updateStatus(
                    IDEMPOTENCY_CHANNEL,
                    transfer.getIdempotencyKey(),
                    TransferStatus.READY_FOR_SETTLEMENT.name());
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("outboxEventId", event.getId());
        payload.put("transferRef", transfer.getTransferRef());
        payload.put("status", TransferStatus.READY_FOR_SETTLEMENT.name());
        payload.put("externalReference", result.getExternalReference());
        payload.put("reference", result.getReference());
        payload.put("poolAvailableBalance", confirmedPoolBalance.availableBalance());
        payload.put("poolHeldAmount", confirmedPoolBalance.heldAmount());

        auditLogService.log(
                "OUTBOX_DISPATCH_SUCCESS",
                ENTITY_TYPE,
                transfer.getTransferRef(),
                SOURCE_SYSTEM,
                payload);

        // ── Lifecycle: ready for T+1 settlement ──────────────────────────────
        flowTracker.markReadyForSettlement(transfer.getTransferRef(), transfer.getBusinessDate());
        eventPublisher.publishQuietly(transfer.getTransferRef(), "TRANSFER_READY_FOR_SETTLEMENT",
                transfer.getBusinessDate(),
                java.util.Map.of("outboxEventId", event.getId(),
                        "externalReference", String.valueOf(result.getExternalReference())),
                SOURCE_SYSTEM);
        recordAttempt(event.getId(), safeRetryCount(event.getRetryCount()),
                "SUCCESS", null, null, null, connectorName);

        dispatchSuccessCounter.increment();
        log.info("Outbox dispatch success: outboxEventId={} transferRef={}",
                event.getId(), transfer.getTransferRef());
    }

    private void finalizeBusinessFailure(Long outboxEventId,
            String transferRef,
            BankDispatchResult result,
            String connectorName) {
        OutboxEventEntity event = getOutboxEventOrThrow(outboxEventId);
        TransferEntity transfer = getTransferOrThrow(transferRef);

        ErrorCatalog catalog = ErrorCatalog.EXT_001;
        FailureClass failureClass = failureClassificationService.classifyBankFailure(result);
        boolean shouldRetry = failureClassificationService.shouldRetry(
                failureClass,
                safeRetryCount(event.getRetryCount()) + 1,
                maxRetry);

        transfer.setErrorCode(catalog.getErrorCode());
        transfer.setErrorMessage(trimMessage(result.getErrorMessage()));
        if (!shouldRetry) {
            boolean rejectedNow = rejectTransferIfOpen(transfer, catalog.getErrorCode());
            PoolBalance releasedPoolBalance = poolService.releaseHold(transfer.getTransferRef());
            transferRepository.save(transfer);

            auditLogService.log(
                    "POOL_HOLD_RELEASED",
                    ENTITY_TYPE,
                    transfer.getTransferRef(),
                    SOURCE_SYSTEM,
                    java.util.Map.of(
                            "transferRef", transfer.getTransferRef(),
                            "reason", "BUSINESS_FAILURE",
                            "availableBalance", releasedPoolBalance.availableBalance(),
                            "heldAmount", releasedPoolBalance.heldAmount()));

            if (rejectedNow && StringUtils.hasText(transfer.getIdempotencyKey())) {
                idempotencyService.updateStatus(
                        IDEMPOTENCY_CHANNEL,
                        transfer.getIdempotencyKey(),
                        TransferStatus.REJECTED.name());
            }
        } else {
            transferRepository.save(transfer);
        }

        int nextRetryCount = safeRetryCount(event.getRetryCount()) + 1;
        boolean isFinal = retryScheduleService.isFinalAttempt(nextRetryCount);

        // AMBIGUOUS: check if PSP already applied credit before scheduling another retry
        if (shouldRetry && failureClass == FailureClass.AMBIGUOUS) {
            OutboxAmbiguousCheckService.CreditStatusResponse creditStatus =
                    ambiguousCheckService.checkCreditStatus(resolveConnectorEndpoint(event), transfer.getTransferRef());
            if (creditStatus.creditApplied()) {
                log.info("Ambiguous credit confirmed APPLIED — settling txnId={}", transfer.getTransferRef());
                finalizeSuccess(event.getId(), transfer.getTransferRef(),
                        BankDispatchResult.success(null, "AMBIGUOUS_CREDIT_CONFIRMED"), connectorName);
                return;
            }
        }

        event.setRetryCount(nextRetryCount);
        event.setStatus(shouldRetry ? OutboxStatus.PENDING : OutboxStatus.FAILED);
        event.setNextRetryAt(shouldRetry ? retryScheduleService.computeNextRetry(nextRetryCount) : null);
        event.setLastError(trimMessage(result.getErrorMessage()));
        event.setFailureClass(failureClass);
        event.setWillRetry(shouldRetry);
        outboxEventRepository.save(event);

        // Auto-reversal on final attempt
        if (!shouldRetry && isFinal && fpre.isAutoReversalEnabled()) {
            autoReversalService.triggerReversal(event, transfer,
                    OutboxAutoReversalService.reasonFor(failureClass));
            pspSuspensionService.checkAndSuspend(transfer.getDestinationBank());
        }

        Map<String, Object> payload = buildErrorPayload(
                catalog,
                event.getId(),
                transfer.getTransferRef(),
                result.getErrorMessage());
        payload.put("downstreamErrorCode", result.getErrorCode());
        payload.put("failureClass", failureClass.name());
        payload.put("attemptNo", nextRetryCount);
        payload.put("maxRetry", maxRetry);
        payload.put("willRetry", shouldRetry);

        auditLogService.log(
                shouldRetry ? "OUTBOX_DISPATCH_RETRY_SCHEDULED" : "OUTBOX_DISPATCH_FAILED",
                ENTITY_TYPE,
                transfer.getTransferRef(),
                SOURCE_SYSTEM,
                payload);

        // ── Lifecycle: REJECTED or RETRY_SCHEDULED ────────────────────────────
        String eventType = shouldRetry ? "TRANSFER_RETRY_SCHEDULED" : "TRANSFER_REJECTED";
        eventPublisher.publishQuietly(transfer.getTransferRef(), eventType,
                transfer.getBusinessDate(),
                java.util.Map.of("outboxEventId", event.getId(),
                        "failureClass",  failureClass.name(),
                        "attemptNo",     nextRetryCount,
                        "willRetry",     shouldRetry),
                SOURCE_SYSTEM);
        if (shouldRetry) {
            webhookPublisher.transferRetryScheduled(transfer.getTransferRef(), transfer.getSourceBank(),
                    java.util.Map.of("transferRef",  transfer.getTransferRef(),
                                     "attemptNo",    nextRetryCount,
                                     "failureClass", failureClass.name()));
        } else {
            webhookPublisher.transferRejected(transfer.getTransferRef(), transfer.getSourceBank(),
                    java.util.Map.of("transferRef",  transfer.getTransferRef(),
                                     "errorCode",    catalog.getErrorCode(),
                                     "failureClass", failureClass.name()));
            flowTracker.markFailed(transfer.getTransferRef(), transfer.getBusinessDate());
        }
        recordAttempt(event.getId(), nextRetryCount,
                shouldRetry ? "RETRY_SCHEDULED" : "FAILED",
                catalog.getErrorCode(), trimMessage(result.getErrorMessage()),
                failureClass.name(), connectorName);

        if (!shouldRetry) {
            dispatchBusinessFailureCounter.increment();
        }
        log.warn("Outbox dispatch downstream failure: outboxEventId={} transferRef={} errorCode={} failureClass={} willRetry={}",
                event.getId(), transfer.getTransferRef(), catalog.getErrorCode(), failureClass, shouldRetry);
    }

    private void finalizeTechnicalFailure(Long outboxEventId,
            String transferRef,
            Exception ex) {
        ErrorCatalog catalog = errorClassifier.classify(ex);
        OutboxEventEntity event = getOutboxEventOrThrow(outboxEventId);
        FailureClass failureClass = failureClassificationService.classifyTechnicalFailure(catalog);

        int nextRetryCount = safeRetryCount(event.getRetryCount()) + 1;
        boolean shouldRetry = failureClassificationService.shouldRetry(failureClass, nextRetryCount, maxRetry);

        TransferEntity transfer = null;
        if (StringUtils.hasText(transferRef)) {
            transfer = transferRepository.findByTransferRef(transferRef).orElse(null);
        }

        if (transfer != null) {
            transfer.setErrorCode(catalog.getErrorCode());
            transfer.setErrorMessage(trimMessage(ex.getMessage()));

            if (failureClassificationService.shouldRejectTransfer(failureClass, shouldRetry)) {
                boolean rejectedNow = rejectTransferIfOpen(transfer, catalog.getErrorCode());
                PoolBalance releasedPoolBalance = poolService.releaseHold(transfer.getTransferRef());
                transferRepository.save(transfer);

                auditLogService.log(
                        "POOL_HOLD_RELEASED",
                        ENTITY_TYPE,
                        transfer.getTransferRef(),
                        SOURCE_SYSTEM,
                        java.util.Map.of(
                                "transferRef", transfer.getTransferRef(),
                                "reason", "TECHNICAL_FAILURE",
                                "availableBalance", releasedPoolBalance.availableBalance(),
                                "heldAmount", releasedPoolBalance.heldAmount()));

                if (rejectedNow && StringUtils.hasText(transfer.getIdempotencyKey())) {
                    idempotencyService.updateStatus(
                            IDEMPOTENCY_CHANNEL,
                            transfer.getIdempotencyKey(),
                            TransferStatus.REJECTED.name());
                }
            } else {
                transferRepository.save(transfer);
            }
        }

        boolean isFinal = retryScheduleService.isFinalAttempt(nextRetryCount);

        event.setRetryCount(nextRetryCount);
        event.setStatus(shouldRetry ? OutboxStatus.PENDING : OutboxStatus.FAILED);
        event.setNextRetryAt(shouldRetry ? retryScheduleService.computeNextRetry(nextRetryCount) : null);
        event.setLastError(trimMessage(ex.getMessage()));
        event.setFailureClass(failureClass);
        event.setWillRetry(shouldRetry);
        outboxEventRepository.save(event);

        // Auto-reversal on final attempt for retryable failure classes
        if (!shouldRetry && isFinal && fpre.isAutoReversalEnabled() && transfer != null) {
            autoReversalService.triggerReversal(event, transfer,
                    OutboxAutoReversalService.reasonFor(failureClass));
            pspSuspensionService.checkAndSuspend(transfer.getDestinationBank());
        }

        Map<String, Object> payload = buildErrorPayload(
                catalog,
                event.getId(),
                StringUtils.hasText(transferRef) ? transferRef : event.getTransferRef(),
                ex.getMessage());
        payload.put("attemptNo", nextRetryCount);
        payload.put("maxRetry", maxRetry);
        payload.put("willRetry", shouldRetry);
        payload.put("failureClass", failureClass.name());

        auditLogService.log(
                shouldRetry ? "OUTBOX_DISPATCH_RETRY_SCHEDULED" : "OUTBOX_DISPATCH_FAILED",
                ENTITY_TYPE,
                StringUtils.hasText(transferRef) ? transferRef : event.getTransferRef(),
                SOURCE_SYSTEM,
                payload);

        // ── Lifecycle: REJECTED or RETRY_SCHEDULED (technical) ────────────────
        String effectiveRef = StringUtils.hasText(transferRef) ? transferRef : event.getTransferRef();
        String techEventType = shouldRetry ? "TRANSFER_RETRY_SCHEDULED" : "TRANSFER_REJECTED";
        eventPublisher.publishQuietly(effectiveRef, techEventType,
                java.time.LocalDate.now(),
                java.util.Map.of("outboxEventId", event.getId(),
                        "failureClass", failureClass.name(),
                        "attemptNo",    nextRetryCount,
                        "technical",    true),
                SOURCE_SYSTEM);
        if (!shouldRetry && transfer != null) {
            flowTracker.markFailed(transfer.getTransferRef(), transfer.getBusinessDate());
            webhookPublisher.transferRejected(transfer.getTransferRef(), transfer.getSourceBank(),
                    java.util.Map.of("transferRef",  transfer.getTransferRef(),
                                     "errorCode",    catalog.getErrorCode(),
                                     "failureClass", failureClass.name(),
                                     "technical",    true));
        } else if (shouldRetry && transfer != null) {
            webhookPublisher.transferRetryScheduled(transfer.getTransferRef(), transfer.getSourceBank(),
                    java.util.Map.of("transferRef",  transfer.getTransferRef(),
                                     "attemptNo",    nextRetryCount,
                                     "failureClass", failureClass.name()));
        }
        recordAttempt(event.getId(), nextRetryCount,
                shouldRetry ? "RETRY_SCHEDULED" : "FAILED",
                catalog.getErrorCode(), trimMessage(ex.getMessage()),
                failureClass.name(), null);

        if (shouldRetry) {
            log.warn("Outbox dispatch retry scheduled: outboxEventId={} transferRef={} errorCode={} attempt={}/{} error={}",
                    event.getId(), transferRef, catalog.getErrorCode(), nextRetryCount, maxRetry, ex.getMessage());
        } else {
            dispatchTechnicalFailureCounter.increment();
            log.error("Outbox dispatch terminal failure: outboxEventId={} transferRef={} errorCode={}",
                    event.getId(), transferRef, catalog.getErrorCode(), ex);
        }
    }

    private Map<String, Object> buildErrorPayload(ErrorCatalog catalog,
            Long outboxEventId,
            String transferRef,
            String errorMessage) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("outboxEventId", outboxEventId);
        payload.put("transferRef", transferRef);
        payload.put("errorCode", catalog.getErrorCode());
        payload.put("category", catalog.getCategory().name());
        payload.put("layer", catalog.getLayer().name());
        payload.put("phase", catalog.getPhase().name());
        payload.put("retryable", catalog.isRetryable());
        payload.put("message", catalog.getDefaultMessage());
        payload.put("errorMessage", trimMessage(errorMessage));
        return payload;
    }

    private OutboxEventEntity getOutboxEventOrThrow(Long outboxEventId) {
        return outboxEventRepository.findById(outboxEventId)
                .orElseThrow(() -> new IllegalStateException(
                        "Outbox event not found for id: " + outboxEventId));
    }

    private TransferEntity getTransferOrThrow(String transferRef) {
        return transferRepository.findByTransferRef(transferRef)
                .orElseThrow(() -> new IllegalStateException(
                        "Transfer not found for transferRef: " + transferRef));
    }

    private boolean rejectTransferIfOpen(TransferEntity transfer, String reasonCode) {
        if (isTerminalStatus(transfer.getStatus())) {
            log.info("Skip reject transition for transferRef={} because status is already terminal: {}",
                    transfer.getTransferRef(), transfer.getStatus());
            return false;
        }
        transferStateMachineService.transition(transfer, TransferStatus.REJECTED, reasonCode);
        return true;
    }

    private boolean isTerminalStatus(TransferStatus status) {
        return status == TransferStatus.REJECTED
                || status == TransferStatus.FAILED
                || status == TransferStatus.SETTLED
                || status == TransferStatus.SUCCESS
                || status == TransferStatus.REFUNDED;
    }

    private String resolveTransferRef(OutboxEventEntity event, DispatchTransferCommand command) {
        if (command != null && StringUtils.hasText(command.getTransferRef())) {
            return command.getTransferRef();
        }
        return event.getTransferRef();
    }

    private String resolveConnectorEndpoint(OutboxEventEntity event) {
        try {
            DispatchTransferCommand command = objectMapper.readValue(event.getPayload(), DispatchTransferCommand.class);
            if (!StringUtils.hasText(command.getConnectorName())) {
                return null;
            }

            return jdbcTemplate.query(
                    "SELECT endpoint_url FROM connector_configs WHERE connector_name = ?",
                    rs -> rs.next() ? rs.getString("endpoint_url") : null,
                    command.getConnectorName());
        } catch (Exception ex) {
            log.debug("Unable to resolve connector endpoint for ambiguous check: outboxEventId={} error={}",
                    event.getId(), ex.getMessage());
            return null;
        }
    }

    private int safeRetryCount(Integer retryCount) {
        return retryCount == null ? 0 : retryCount;
    }

    private String trimMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }

        String trimmed = message.trim();
        if (trimmed.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return trimmed;
        }

        return trimmed.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }

    /**
     * Write one row to {@code outbox_attempts} — fire-and-quiet so a recording
     * failure never aborts the main dispatch flow.
     */
    private void recordAttempt(Long outboxMessageId,
                                int attemptNumber,
                                String status,
                                String errorCode,
                                String errorMessage,
                                String failureClass,
                                String connectorName) {
        try {
            jdbcTemplate.update(OUTBOX_ATTEMPT_SQL,
                    outboxMessageId, attemptNumber, status,
                    errorCode, errorMessage, failureClass,
                    connectorName, null /* duration_ms — not tracked at this granularity */);
        } catch (Exception ex) {
            log.warn("Failed to record outbox_attempt outboxMessageId={} attempt={} — {}",
                    outboxMessageId, attemptNumber, ex.getMessage());
        }
    }
}
