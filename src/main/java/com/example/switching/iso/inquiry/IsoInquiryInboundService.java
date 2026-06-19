package com.example.switching.iso.inquiry;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.switching.audit.service.AuditLogService;
import com.example.switching.common.util.MaskingUtil;

@Service
public class IsoInquiryInboundService {

    private static final Logger log = LoggerFactory.getLogger(IsoInquiryInboundService.class);

    private static final String ISO_CHANNEL_ID = "ISO20022_XML";
    private static final int INQUIRY_TTL_MINUTES = 15;

    private final Acmt023XmlParser parser;
    private final Acmt024XmlResponseBuilder responseBuilder;
    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;

    public IsoInquiryInboundService(
            Acmt023XmlParser parser,
            Acmt024XmlResponseBuilder responseBuilder,
            JdbcTemplate jdbcTemplate,
            AuditLogService auditLogService
    ) {
        this.parser = parser;
        this.responseBuilder = responseBuilder;
        this.jdbcTemplate = jdbcTemplate;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public String handle(String xBankCode, String xmlBody) {
        log.debug("ACMT.023 received from bankCode={}", xBankCode);
        if (log.isDebugEnabled()) {
            log.debug("ACMT.023 payload (accounts masked): {}", MaskingUtil.maskXmlAccounts(xmlBody));
        }

        Acmt023InquiryRequest request;

        try {
            request = parser.parse(xmlBody);
            auditAcmt023InboundReceived(request);
        } catch (Exception e) {
            Acmt023InquiryRequest fallback = new Acmt023InquiryRequest();
            fallback.setMessageId("UNKNOWN");
            return responseBuilder.rejected(fallback, "FF01", e.getMessage());
        }

        if (!StringUtils.hasText(xBankCode) || !xBankCode.trim().equals(request.getSourceBank())) {
            return responseBuilder.rejected(
                    request,
                    "FF01",
                    "X-Bank-Code must match inquiry source bank"
            );
        }

        if (!isParticipantActive(request.getSourceBank())) {
            return rejectAndSave(request, "BANK_INACTIVE", "Source participant is inactive or missing");
        }

        if (!isParticipantActive(request.getDestinationBank())) {
            return rejectAndSave(request, "BANK_INACTIVE", "Destination participant is inactive or missing");
        }

        boolean accountFound = StringUtils.hasText(request.getCreditorAccount());
        boolean bankAvailable = true;
        boolean eligible = accountFound && bankAvailable;

        if (!eligible) {
            return rejectAndSave(request, "AC01", "Creditor account is missing or invalid");
        }

        String existingInquiryRef = findExistingInquiryRef(request.getMessageId());
        if (StringUtils.hasText(existingInquiryRef)) {
            String responseXml = responseBuilder.accepted(request, existingInquiryRef);
            auditAcmt024ResponseCreated(request, existingInquiryRef, "MTCH", "EXISTING");
            return responseXml;
        }

        String inquiryRef = generateInquiryRef();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(INQUIRY_TTL_MINUTES);

        int inserted = jdbcTemplate.update(
                """
                INSERT INTO inquiries (
                    inquiry_ref,
                    channel_id,
                    message_id,
                    instruction_id,
                    end_to_end_id,
                    source_bank,
                    destination_bank,
                    debtor_account,
                    creditor_account,
                    amount,
                    currency,
                    reference,
                    status,
                    account_found,
                    bank_available,
                    eligible_for_transfer,
                    error_code,
                    error_message,
                    expires_at,
                    used_by_transaction_ref,
                    business_date,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, COALESCE(?, 'LAK'), ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_DATE, ?, ?)
                ON CONFLICT (channel_id, message_id, business_date) DO NOTHING
                """,
                inquiryRef,
                ISO_CHANNEL_ID,
                request.getMessageId(),
                request.getInstructionId(),
                request.getEndToEndId(),
                request.getSourceBank(),
                request.getDestinationBank(),
                null,
                clean(request.getCreditorAccount()),
                request.getAmount(),
                clean(request.getCurrency()),
                clean(request.getReference()),
                "ELIGIBLE",
                true,
                true,
                true,
                null,
                null,
                expiresAt,
                null,
                now,
                now
        );

        if (inserted == 0) {
            /*
             * Concurrent race: another thread inserted the same (channel_id, message_id)
             * first. ON CONFLICT DO NOTHING keeps the transaction alive so we can
             * safely read the committed winner row.
             */
            String winnerRef = findCurrentInquiryRef(request.getMessageId());
            if (StringUtils.hasText(winnerRef)) {
                log.debug("Concurrent ACMT.023 race resolved: messageId={} existingRef={}",
                        request.getMessageId(), winnerRef);
                auditAcmt024ResponseCreated(request, winnerRef, "MTCH", "EXISTING");
                return responseBuilder.accepted(request, winnerRef);
            }
            throw new IllegalStateException("Concurrent insert conflict but no existing row found for messageId=" + request.getMessageId());
        }

        writeStatusHistory(inquiryRef, "ELIGIBLE", null, now);
        auditInquiryCreated(request, inquiryRef, "ELIGIBLE", true, expiresAt, null, null);

        String responseXml = responseBuilder.accepted(request, inquiryRef);
        auditAcmt024ResponseCreated(request, inquiryRef, "MTCH", "CREATED");
        return responseXml;
    }

    private String rejectAndSave(Acmt023InquiryRequest request, String code, String message) {
        String inquiryRef = generateInquiryRef();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(INQUIRY_TTL_MINUTES);

        jdbcTemplate.update(
                """
                INSERT INTO inquiries (
                    inquiry_ref,
                    channel_id,
                    message_id,
                    instruction_id,
                    end_to_end_id,
                    source_bank,
                    destination_bank,
                    debtor_account,
                    creditor_account,
                    amount,
                    currency,
                    reference,
                    status,
                    account_found,
                    bank_available,
                    eligible_for_transfer,
                    error_code,
                    error_message,
                    expires_at,
                    used_by_transaction_ref,
                    business_date,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, COALESCE(?, 'LAK'), ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_DATE, ?, ?)
                """,
                inquiryRef,
                ISO_CHANNEL_ID,
                request.getMessageId(),
                request.getInstructionId(),
                request.getEndToEndId(),
                request.getSourceBank(),
                request.getDestinationBank(),
                null,
                clean(request.getCreditorAccount()),
                request.getAmount(),
                clean(request.getCurrency()),
                clean(request.getReference()),
                "REJECTED",
                false,
                false,
                false,
                code,
                message,
                expiresAt,
                null,
                now,
                now
        );

        writeStatusHistory(inquiryRef, "REJECTED", code, now);
        auditInquiryCreated(request, inquiryRef, "REJECTED", false, expiresAt, code, message);

        String responseXml = responseBuilder.rejected(request, code, message);
        auditAcmt024ResponseCreated(request, inquiryRef, "NMTC", "REJECTED");
        return responseXml;
    }

    private boolean isParticipantActive(String bankCode) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM participants
                WHERE bank_code = ?
                  AND status = 'ACTIVE'
                """,
                Integer.class,
                bankCode
        );

        return count != null && count > 0;
    }

    private String findExistingInquiryRef(String messageId) {
        if (!StringUtils.hasText(messageId)) {
            return null;
        }

        return jdbcTemplate.query(
                """
                SELECT inquiry_ref
                FROM inquiries
                WHERE channel_id = ?
                  AND message_id = ?
                ORDER BY business_date DESC, id DESC
                LIMIT 1
                """,
                rs -> rs.next() ? rs.getString("inquiry_ref") : null,
                ISO_CHANNEL_ID,
                messageId.trim()
        );
    }

    /**
     * Reads the committed inquiry_ref for a given messageId using a locking read.
     *
     * FOR SHARE bypasses the REPEATABLE READ snapshot so the race-losing
     * thread can see the winner's row even if that row was committed after the loser's
     * transaction started.  Used only in the concurrent-INSERT catch block.
     */
    private String findCurrentInquiryRef(String messageId) {
        if (!StringUtils.hasText(messageId)) {
            return null;
        }

        return jdbcTemplate.query(
                """
                SELECT inquiry_ref
                FROM inquiries
                WHERE channel_id = ?
                  AND message_id = ?
                  AND business_date = CURRENT_DATE
                FOR SHARE
                """,
                rs -> rs.next() ? rs.getString("inquiry_ref") : null,
                ISO_CHANNEL_ID,
                messageId.trim()
        );
    }

    private void auditAcmt023InboundReceived(Acmt023InquiryRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageType", "ACMT_023");
        payload.put("messageId", request.getMessageId());
        payload.put("instructionId", request.getInstructionId());
        payload.put("endToEndId", request.getEndToEndId());
        payload.put("sourceBank", request.getSourceBank());
        payload.put("destinationBank", request.getDestinationBank());
        payload.put("debtorAccount", null);
        payload.put("creditorAccount", MaskingUtil.maskAccount(clean(request.getCreditorAccount())));
        payload.put("amount", request.getAmount());
        payload.put("currency", clean(request.getCurrency()));
        payload.put("reference", clean(request.getReference()));

        auditLogService.log(
                "ISO_ACMT023_INBOUND_RECEIVED",
                "INQUIRY",
                request.getMessageId(),
                ISO_CHANNEL_ID,
                payload
        );
    }

    private void auditInquiryCreated(
            Acmt023InquiryRequest request,
            String inquiryRef,
            String status,
            boolean eligibleForTransfer,
            LocalDateTime expiresAt,
            String failureCode,
            String failureMessage
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("inquiryRef", inquiryRef);
        payload.put("messageId", request.getMessageId());
        payload.put("instructionId", request.getInstructionId());
        payload.put("endToEndId", request.getEndToEndId());
        payload.put("sourceBank", request.getSourceBank());
        payload.put("destinationBank", request.getDestinationBank());
        payload.put("debtorAccount", null);
        payload.put("creditorAccount", MaskingUtil.maskAccount(clean(request.getCreditorAccount())));
        payload.put("amount", request.getAmount());
        payload.put("currency", clean(request.getCurrency()));
        payload.put("status", status);
        payload.put("eligibleForTransfer", eligibleForTransfer);
        payload.put("expiresAt", expiresAt);
        payload.put("failureCode", failureCode);
        payload.put("failureMessage", failureMessage);

        auditLogService.log(
                "ISO_INQUIRY_CREATED",
                "INQUIRY",
                inquiryRef,
                ISO_CHANNEL_ID,
                payload
        );
    }

    private void auditAcmt024ResponseCreated(
            Acmt023InquiryRequest request,
            String inquiryRef,
            String verificationStatus,
            String responseReason
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageType", "ACMT_024");
        payload.put("inquiryRef", inquiryRef);
        payload.put("originalMessageId", request.getMessageId());
        payload.put("instructionId", request.getInstructionId());
        payload.put("endToEndId", request.getEndToEndId());
        payload.put("verificationStatus", verificationStatus);
        payload.put("responseReason", responseReason);

        auditLogService.log(
                "ISO_ACMT024_RESPONSE_CREATED",
                "INQUIRY",
                inquiryRef,
                ISO_CHANNEL_ID,
                payload
        );
    }

    private String generateInquiryRef() {
        String timestamp = DateTimeFormatter
                .ofPattern("yyyyMMddHHmmss")
                .format(LocalDateTime.now());

        String randomSuffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase();

        return "INQ-" + timestamp + "-" + randomSuffix;
    }

    /**
     * Writes a status transition row to {@code inquiry_status_history} for the ISO path.
     * The table has no FK to {@code inquiries}, so ISO inquiry refs (prefixed "INQ-") are
     * accepted safely.  This gives a unified history view across both JSON and ISO paths.
     */
    private void writeStatusHistory(String inquiryRef, String status, String reasonCode, LocalDateTime now) {
        jdbcTemplate.update(
                """
                INSERT INTO inquiry_status_history (inquiry_ref, status, reason_code, created_at)
                VALUES (?, ?, ?, ?)
                """,
                inquiryRef, status, reasonCode, now
        );
    }

    private String clean(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return value.trim();
    }
}
