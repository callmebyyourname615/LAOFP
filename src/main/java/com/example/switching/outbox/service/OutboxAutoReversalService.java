package com.example.switching.outbox.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.example.switching.audit.service.AuditLogService;
import com.example.switching.fpre.exception.AutoReversalException;
import com.example.switching.iso.entity.IsoMessageEntity;
import com.example.switching.iso.enums.IsoMessageDirection;
import com.example.switching.iso.enums.IsoMessageType;
import com.example.switching.iso.enums.IsoSecurityStatus;
import com.example.switching.iso.enums.IsoValidationStatus;
import com.example.switching.iso.mapper.Camt056XmlBuilder;
import com.example.switching.iso.repository.IsoMessageRepository;
import com.example.switching.outbox.entity.OutboxEventEntity;
import com.example.switching.outbox.entity.ReversalLogEntity;
import com.example.switching.outbox.enums.FailureClass;
import com.example.switching.outbox.repository.ReversalLogRepository;
import com.example.switching.transfer.entity.TransferEntity;

/**
 * FPRE auto-reversal — triggered when all retry attempts are exhausted.
 *
 * <p>Steps:
 * <ol>
 *   <li>Guard against duplicate reversals (idempotency).</li>
 *   <li>Insert a {@code reversal_log} row in INITIATED state.</li>
 *   <li>Build a camt.056 (FIToFIPaymentCancellationRequest) XML message and persist
 *       it to {@code iso_messages} as an OUTBOUND record — this is the audit trail
 *       of the credit-return notice sent to the originating bank.</li>
 *   <li>Mark the reversal COMPLETED once the ISO message is stored.
 *       Actual network delivery to the originating bank requires a dedicated outbound
 *       connector call wired to the source-bank's endpoint.</li>
 * </ol>
 */
@Service
public class OutboxAutoReversalService {

    private static final Logger log = LoggerFactory.getLogger(OutboxAutoReversalService.class);
    private static final String SOURCE_SYSTEM = "FPRE_REVERSAL";
    private static final String ENTITY_TYPE   = "TRANSFER";
    private static final String CAMT056_REASON_COMPLIANCE = "LEGL";
    private static final String CAMT056_REASON_DEFAULT    = "FOCR";

    private final ReversalLogRepository  reversalLogRepository;
    private final IsoMessageRepository   isoMessageRepository;
    private final Camt056XmlBuilder      camt056XmlBuilder;
    private final JdbcTemplate           jdbcTemplate;
    private final AuditLogService        auditLogService;

    public OutboxAutoReversalService(ReversalLogRepository reversalLogRepository,
                                     IsoMessageRepository isoMessageRepository,
                                     Camt056XmlBuilder camt056XmlBuilder,
                                     JdbcTemplate jdbcTemplate,
                                     AuditLogService auditLogService) {
        this.reversalLogRepository = reversalLogRepository;
        this.isoMessageRepository  = isoMessageRepository;
        this.camt056XmlBuilder     = camt056XmlBuilder;
        this.jdbcTemplate          = jdbcTemplate;
        this.auditLogService       = auditLogService;
    }

    /**
     * Trigger auto-reversal for an outbox event that exhausted all FPRE retries.
     *
     * @param event    the terminal outbox event
     * @param transfer the associated transfer
     * @param reason   reversal reason: MAX_RETRIES | COMPLIANCE_BLOCK | EXPIRED
     * @return the persisted {@link ReversalLogEntity}
     */
    public ReversalLogEntity triggerReversal(OutboxEventEntity event,
                                             TransferEntity transfer,
                                             String reason) {
        String originalTxnId   = transfer.getTransferRef();
        String destinationBank = transfer.getDestinationBank();

        if (reversalLogRepository.existsByOriginalTxnId(originalTxnId)) {
            log.warn("Auto-reversal already exists for txnId={} — skipping duplicate", originalTxnId);
            return reversalLogRepository
                    .findAll()
                    .stream()
                    .filter(r -> originalTxnId.equals(r.getOriginalTxnId()))
                    .findFirst()
                    .orElseThrow();
        }

        LocalDateTime now = LocalDateTime.now();
        String reversalTxnId = generateReversalId();

        ReversalLogEntity reversal = new ReversalLogEntity();
        reversal.setOriginalTxnId(originalTxnId);
        reversal.setReversalTxnId(reversalTxnId);
        reversal.setDestinationBank(destinationBank);
        reversal.setReason(reason);
        reversal.setStatus("INITIATED");
        reversal.setFailureClass(event.getFailureClass() != null ? event.getFailureClass().name() : null);
        reversal.setTriggeredAt(now);
        reversal.setCreatedAt(now);

        reversal = reversalLogRepository.save(reversal);

        // Build and persist the CAMT.056 cancellation request as an outbound ISO message.
        // This gives auditors a full ISO 20022 trail of the credit-return notice.
        String camt056Xml = buildCamt056(reversalTxnId, transfer, reason);
        Long isoMessageId = persistCamt056IsoMessage(originalTxnId, transfer.getSourceBank(), camt056Xml, now);

        // Mark complete once the ISO record is stored.
        // Actual network dispatch to the source bank requires wiring up the source-bank connector.
        reversal.setStatus("COMPLETED");
        reversal.setCompletedAt(now);
        reversal.setUpdatedAt(now);
        reversal = reversalLogRepository.save(reversal);

        auditLogService.log(
                "FPRE_AUTO_REVERSAL_TRIGGERED",
                ENTITY_TYPE,
                originalTxnId,
                SOURCE_SYSTEM,
                Map.of(
                        "reversalId",      reversal.getReversalId(),
                        "reversalTxnId",   reversalTxnId,
                        "destinationBank", destinationBank,
                        "sourceBank",      transfer.getSourceBank() != null ? transfer.getSourceBank() : "",
                        "reason",          reason,
                        "camt056MsgId",    isoMessageId != null ? isoMessageId : "N/A",
                        "outboxEventId",   event.getId()));

        log.warn("FPRE auto-reversal triggered: txnId={} destinationBank={} reason={} reversalId={} camt056IsoId={}",
                originalTxnId, destinationBank, reason, reversal.getReversalId(), isoMessageId);

        return reversal;
    }

    /**
     * Derive the reversal reason from the outbox failure class.
     */
    public static String reasonFor(FailureClass failureClass) {
        if (failureClass == FailureClass.PERMANENT_COMPLIANCE) {
            return "COMPLIANCE_BLOCK";
        }
        return "MAX_RETRIES";
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String buildCamt056(String cancellationMsgId, TransferEntity transfer, String reason) {
        String reasonCode = "COMPLIANCE_BLOCK".equals(reason) ? CAMT056_REASON_COMPLIANCE : CAMT056_REASON_DEFAULT;
        String originalMsgId = "MSG-" + transfer.getTransferRef();
        BigDecimal amount = transfer.getAmount();
        String currency = transfer.getCurrency();

        return camt056XmlBuilder.build(
                cancellationMsgId,
                originalMsgId,
                "E2E-" + transfer.getTransferRef(),
                transfer.getTransferRef(),
                amount,
                currency,
                reasonCode,
                reason);
    }

    private Long persistCamt056IsoMessage(String transferRef, String sourceBank, String xml, LocalDateTime now) {
        try {
            IsoMessageEntity entity = new IsoMessageEntity();
            entity.setCorrelationRef(transferRef);
            entity.setTransferRef(transferRef);
            entity.setMessageId("CAMT056-" + transferRef);
            entity.setMessageType(IsoMessageType.CAMT_056);
            entity.setDirection(IsoMessageDirection.OUTBOUND);
            entity.setPlainPayload(xml);
            entity.setSecurityStatus(IsoSecurityStatus.PLAIN);
            entity.setValidationStatus(IsoValidationStatus.NOT_VALIDATED);

            IsoMessageEntity saved = isoMessageRepository.save(entity);

            // plainPayload is @Transient — persist separately to iso_message_payloads.
            int sizeBytes = xml != null ? xml.getBytes(java.nio.charset.StandardCharsets.UTF_8).length : 0;
            jdbcTemplate.update("""
                    INSERT INTO iso_message_payloads
                      (iso_message_id, payload_type, plain_payload, encrypted_payload,
                       payload_size_bytes, business_date)
                    VALUES (?, 'REQUEST', ?, NULL, ?, CURRENT_DATE)
                    """,
                    saved.getId(), xml, sizeBytes);

            return saved.getId();
        } catch (Exception ex) {
            log.error("Failed to persist CAMT.056 ISO message for txnId={}: {}", transferRef, ex.getMessage());
            throw new AutoReversalException("Failed to persist CAMT.056 ISO message for txnId: " + transferRef, ex);
        }
    }

    private String generateReversalId() {
        return "REV-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}
