package com.example.switching.iso.inbound;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.audit.service.AuditLogService;
import com.example.switching.common.util.TransferRefGenerator;
import com.example.switching.iso.entity.IsoMessageEntity;
import com.example.switching.iso.enums.IsoMessageDirection;
import com.example.switching.iso.enums.IsoMessageType;
import com.example.switching.iso.enums.IsoSecurityStatus;
import com.example.switching.iso.enums.IsoValidationStatus;
import com.example.switching.iso.mapper.Pacs008XmlBuilder;
import com.example.switching.iso.repository.IsoMessageRepository;
import com.example.switching.iso.security.IsoMessageCryptoService;
import com.example.switching.transfer.entity.TransferEntity;
import com.example.switching.transfer.entity.TransferStatusHistoryEntity;
import com.example.switching.transfer.enums.TransferStatus;
import com.example.switching.transfer.repository.TransferRepository;
import com.example.switching.transfer.repository.TransferStatusHistoryRepository;

@Service
public class InboundPacs008PersistenceService {

    private static final String ISO_INBOUND_CHANNEL_ID = "ISO20022_XML";
    private static final String MESSAGE_TYPE_PACS_008 = "PACS_008";

    private final TransferRefGenerator transferRefGenerator;
    private final TransferRepository transferRepository;
    private final TransferStatusHistoryRepository transferStatusHistoryRepository;
    private final IsoMessageRepository isoMessageRepository;
    private final IsoMessageCryptoService isoMessageCryptoService;
    private final Pacs008XmlBuilder pacs008XmlBuilder;
    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;

    public InboundPacs008PersistenceService(
            TransferRefGenerator transferRefGenerator,
            TransferRepository transferRepository,
            TransferStatusHistoryRepository transferStatusHistoryRepository,
            IsoMessageRepository isoMessageRepository,
            IsoMessageCryptoService isoMessageCryptoService,
            Pacs008XmlBuilder pacs008XmlBuilder,
            JdbcTemplate jdbcTemplate,
            AuditLogService auditLogService
    ) {
        this.transferRefGenerator = transferRefGenerator;
        this.transferRepository = transferRepository;
        this.transferStatusHistoryRepository = transferStatusHistoryRepository;
        this.isoMessageRepository = isoMessageRepository;
        this.isoMessageCryptoService = isoMessageCryptoService;
        this.pacs008XmlBuilder = pacs008XmlBuilder;
        this.jdbcTemplate = jdbcTemplate;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public InboundPacs008PersistResult persistAcceptedInboundPacs008(
            Pacs008InboundRequest request,
            String rawXml
    ) {
        String transferRef = transferRefGenerator.generate();
        LocalDateTime now = LocalDateTime.now();

        String clientTransferId = firstNonBlank(
                request.getInstructionId(),
                request.getEndToEndId(),
                request.getMessageId(),
                transferRef
        );

        /*
         * ISO-only idempotency:
         * If a member bank retries the exact same PACS.008 instruction,
         * return the existing transferRef instead of creating a duplicate transfer row.
         *
         * This must run before mandatory inquiry validation because the inquiry
         * may already be marked USED after the first successful submission.
         */
        Optional<String> existingTransferRef = findExistingIsoInboundTransferRef(clientTransferId);
        if (existingTransferRef.isPresent()) {
            return new InboundPacs008PersistResult(existingTransferRef.get());
        }

        /*
         * Mandatory rule:
         * Inquiry must exist before PACS.008 transfer/payment.
         */
        validateMandatoryIsoInquiry(request);

        RouteResolution route = resolveRoute(
                request.getDebtorAgentBic(),
                request.getCreditorAgentBic(),
                MESSAGE_TYPE_PACS_008
        );

        String idempotencyKey = "ISO20022:"
                + safePart(request.getMessageId())
                + ":"
                + safePart(request.getInstructionId())
                + ":"
                + safePart(request.getEndToEndId());

        TransferEntity transfer = new TransferEntity();

        setValue(transfer, "transferRef", transferRef);

        setValueIfPresent(transfer, ISO_INBOUND_CHANNEL_ID, "channelId", "channelID");
        setValueIfPresent(transfer, clientTransferId, "clientTransferId", "clientTransactionId");
        setValueIfPresent(transfer, idempotencyKey, "idempotencyKey");

        setValueIfPresent(transfer, request.getInquiryRef(), "inquiryRef");

        setValueIfPresent(transfer, request.getDebtorAgentBic(), "sourceBankCode", "sourceBank");
        setValueIfPresent(transfer, request.getCreditorAgentBic(), "destinationBankCode", "destinationBank");

        setValueIfPresent(transfer, request.getDebtorAccount(), "sourceAccountNo", "debtorAccount");
        setValueIfPresent(transfer, request.getCreditorAccount(), "destinationAccountNo", "creditorAccount");

        setValueIfPresent(transfer, "ISO INBOUND RECEIVER", "destinationAccountName");
        setValueIfPresent(transfer, request.getAmount(), "amount");
        setValueIfPresent(transfer, request.getCurrency(), "currency");
        setValueIfPresent(transfer, request.getRemittanceInformation(), "reference");

        setValueIfPresent(transfer, route.routeCode(), "routeCode");
        setValueIfPresent(transfer, route.connectorName(), "connectorName");
        setValueIfPresent(transfer, request.getInquiryRef(), "flowRef");

        setValueIfPresent(transfer, null, "externalReference");
        setValueIfPresent(transfer, null, "errorCode");
        setValueIfPresent(transfer, null, "errorMessage");

        setEnumOrStringValue(transfer, "status", TransferStatus.ACCEPTED);

        setValueIfPresent(transfer, now, "createdAt", "createdDate", "createdOn");
        setValueIfPresent(transfer, now, "updatedAt", "updatedDate", "updatedOn");

        transferRepository.save(transfer);

        saveTransferHistory(transferRef, now);

        saveInboundIsoMessage(transferRef, request, rawXml, now);

        /*
         * ISO-IN-1B.2A:
         * Save an OUTBOUND PACS.008 and enqueue TRANSFER_DISPATCH so the existing
         * outbox worker can continue the same dispatch path as the old JSON flow.
         */
        Long outboundIsoMessageId = saveOutboundIsoMessage(transfer, request, now);
        enqueueTransferDispatchOutbox(transferRef, request, route, outboundIsoMessageId, now);
        markInquiryUsed(request.getInquiryRef(), transferRef, now);

        return new InboundPacs008PersistResult(transferRef);
    }

    private void saveTransferHistory(String transferRef, LocalDateTime now) {
        TransferStatusHistoryEntity history = new TransferStatusHistoryEntity();

        setValueIfPresent(history, transferRef, "transferRef");
        setEnumOrStringValue(history, "status", TransferStatus.ACCEPTED);
        setValueIfPresent(history, null, "reasonCode");
        setValueIfPresent(
                history,
                "Inbound PACS.008 accepted and queued for dispatch",
                "message",
                "description",
                "reason",
                "remarks",
                "note"
        );
        setValueIfPresent(history, now, "createdAt", "createdDate", "createdOn");

        transferStatusHistoryRepository.save(history);
    }

    private void saveInboundIsoMessage(
            String transferRef,
            Pacs008InboundRequest request,
            String rawXml,
            LocalDateTime now
    ) {
        IsoMessageEntity isoMessage = new IsoMessageEntity();

        setValueIfPresent(isoMessage, transferRef, "correlationRef");
        setValueIfPresent(isoMessage, transferRef, "transferRef");
        setValueIfPresent(isoMessage, request.getInquiryRef(), "inquiryRef");

        setValueIfPresent(isoMessage, request.getMessageId(), "messageId");
        setValueIfPresent(isoMessage, request.getEndToEndId(), "endToEndId");

        setEnumOrStringValue(isoMessage, "messageType", IsoMessageType.PACS_008);
        setEnumOrStringValue(isoMessage, "direction", IsoMessageDirection.INBOUND);
        setEnumOrStringValue(isoMessage, "validationStatus", IsoValidationStatus.VALID);
        setEnumOrStringValue(isoMessage, "securityStatus", IsoSecurityStatus.PLAIN);

        setXmlPayload(isoMessage, rawXml);

        setValueIfPresent(isoMessage, null, "errorCode");
        setValueIfPresent(isoMessage, null, "errorMessage");
        setValueIfPresent(isoMessage, now, "createdAt", "createdDate", "createdOn");
        setValueIfPresent(isoMessage, now, "updatedAt", "updatedDate", "updatedOn");

        isoMessageRepository.save(isoMessage);
    }

    private Long saveOutboundIsoMessage(
            TransferEntity transfer,
            Pacs008InboundRequest request,
            LocalDateTime now
    ) {
        String transferRef = transfer.getTransferRef();
        String messageId = "MSG-" + transferRef;
        String endToEndId = request.getEndToEndId() != null ? request.getEndToEndId() : "E2E-" + transferRef;

        String outboundXml = pacs008XmlBuilder.build(transfer, messageId, endToEndId);
        String encryptedPayload = isoMessageCryptoService.encrypt(outboundXml);

        IsoMessageEntity isoMessage = new IsoMessageEntity();

        setValueIfPresent(isoMessage, transferRef, "correlationRef");
        setValueIfPresent(isoMessage, transferRef, "transferRef");
        setValueIfPresent(isoMessage, request.getInquiryRef(), "inquiryRef");

        setValueIfPresent(isoMessage, messageId, "messageId");
        setValueIfPresent(isoMessage, endToEndId, "endToEndId");

        setEnumOrStringValue(isoMessage, "messageType", IsoMessageType.PACS_008);
        setEnumOrStringValue(isoMessage, "direction", IsoMessageDirection.OUTBOUND);
        setEnumOrStringValue(isoMessage, "validationStatus", IsoValidationStatus.NOT_VALIDATED);
        setEnumOrStringValue(isoMessage, "securityStatus", IsoSecurityStatus.ENCRYPTED);

        setEncryptedPayload(isoMessage, encryptedPayload);

        setValueIfPresent(isoMessage, null, "errorCode");
        setValueIfPresent(isoMessage, null, "errorMessage");
        setValueIfPresent(isoMessage, now, "createdAt", "createdDate", "createdOn");
        setValueIfPresent(isoMessage, now, "updatedAt", "updatedDate", "updatedOn");

        IsoMessageEntity saved = isoMessageRepository.save(isoMessage);
        Long isoMessageId = getLongValue(saved, "id", "isoMessageId");

        if (isoMessageId == null) {
            throw new IllegalStateException("Unable to resolve saved outbound ISO message id for transferRef=" + transferRef);
        }

        saveIsoPayload(isoMessageId, null, encryptedPayload);

        return isoMessageId;
    }

    private void saveIsoPayload(Long isoMessageId, String plainPayload, String encryptedPayload) {
        if (!hasTable("iso_message_payloads")) {
            return;
        }

        int payloadSizeBytes = 0;
        if (plainPayload != null) {
            payloadSizeBytes += plainPayload.getBytes(StandardCharsets.UTF_8).length;
        }
        if (encryptedPayload != null) {
            payloadSizeBytes += encryptedPayload.getBytes(StandardCharsets.UTF_8).length;
        }

        jdbcTemplate.update(
                """
                INSERT INTO iso_message_payloads (
                    iso_message_id,
                    payload_type,
                    plain_payload,
                    encrypted_payload,
                    payload_size_bytes,
                    business_date
                )
                VALUES (?, 'REQUEST', ?, ?, ?, CURRENT_DATE)
                """,
                isoMessageId,
                plainPayload,
                encryptedPayload,
                payloadSizeBytes
        );
    }

    private void enqueueTransferDispatchOutbox(
            String transferRef,
            Pacs008InboundRequest request,
            RouteResolution route,
            Long outboundIsoMessageId,
            LocalDateTime now
    ) {
        /*
         * IMPORTANT:
         * This JSON must match DispatchTransferCommand exactly.
         * Do not add fields such as messageType, isoMessageType, inquiryRef, or reference.
         * The worker currently fails on unknown JSON properties.
         */
        String payloadJson = """
                {
                  "transferRef": "%s",
                  "sourceBank": "%s",
                  "destinationBank": "%s",
                  "debtorAccount": "%s",
                  "creditorAccount": "%s",
                  "amount": %s,
                  "currency": "%s",
                  "routeCode": "%s",
                  "connectorName": "%s",
                  "isoMessageId": %d
                }
                """.formatted(
                escapeJson(transferRef),
                escapeJson(request.getDebtorAgentBic()),
                escapeJson(request.getCreditorAgentBic()),
                escapeJson(request.getDebtorAccount()),
                escapeJson(request.getCreditorAccount()),
                request.getAmount(),
                escapeJson(request.getCurrency()),
                escapeJson(route.routeCode()),
                escapeJson(route.connectorName()),
                outboundIsoMessageId
        );

        Map<String, Object> values = new LinkedHashMap<>();

        /*
         * Core outbox_messages column: maps the transaction to this outbox row.
         */
        putIfColumnExists(values, "transaction_ref", transferRef);

        /*
         * Optional/common outbox columns. They are inserted only if the column exists.
         */
        putIfColumnExists(values, "aggregate_type", "TRANSFER");
        putIfColumnExists(values, "aggregate_id", transferRef);
        putIfColumnExists(values, "event_type", "TRANSFER_DISPATCH");

        putIfColumnExists(values, "message_type", "TRANSFER_DISPATCH");
        putIfColumnExists(values, "payload", payloadJson);
        putIfColumnExists(values, "payload_json", payloadJson);

        putIfColumnExists(values, "route_code", route.routeCode());
        putIfColumnExists(values, "connector_name", route.connectorName());

        putIfColumnExists(values, "status", "PENDING");
        putIfColumnExists(values, "retry_count", 0);

        putIfColumnExists(values, "last_error", null);
        putIfColumnExists(values, "processed_at", null);
        putIfColumnExists(values, "next_retry_at", now);

        putIfColumnExists(values, "created_at", now);
        putIfColumnExists(values, "updated_at", now);

        if (values.isEmpty()) {
            throw new IllegalStateException("Cannot enqueue outbox event because no known outbox_messages columns were found");
        }

        String columns = String.join(",", values.keySet());
        String placeholders = String.join(",", values.keySet().stream().map(key -> "?").toList());

        jdbcTemplate.update(
                "INSERT INTO outbox_messages (" + columns + ") VALUES (" + placeholders + ")",
                values.values().toArray()
        );
    }

    private Optional<String> findExistingIsoInboundTransferRef(String clientTransferId) {
        if (clientTransferId == null || clientTransferId.isBlank()) {
            return Optional.empty();
        }

        return jdbcTemplate.query(
                """
                SELECT transaction_ref
                FROM transactions
                WHERE channel_id = ?
                  AND client_transaction_id = ?
                ORDER BY id DESC
                LIMIT 1
                """,
                rs -> {
                    if (!rs.next()) {
                        return Optional.empty();
                    }

                    return Optional.ofNullable(rs.getString("transaction_ref"));
                },
                ISO_INBOUND_CHANNEL_ID,
                clientTransferId.trim()
        );
    }

    private RouteResolution resolveRoute(String sourceBank, String destinationBank, String messageType) {
        validateParticipantActive(sourceBank, "source");
        validateParticipantActive(destinationBank, "destination");

        Optional<RouteResolution> route = jdbcTemplate.query(
                """
                SELECT route_code, connector_name
                FROM routing_rules
                WHERE source_bank = ?
                  AND destination_bank = ?
                  AND message_type = ?
                  AND enabled = true
                ORDER BY priority ASC, id ASC
                LIMIT 1
                """,
                rs -> {
                    if (!rs.next()) {
                        return Optional.empty();
                    }

                    return Optional.of(new RouteResolution(
                            rs.getString("route_code"),
                            rs.getString("connector_name")
                    ));
                },
                sourceBank,
                destinationBank,
                messageType
        );

        return route.orElseThrow(() -> new IllegalStateException(
                "No enabled route found for " + sourceBank + " -> " + destinationBank + " / " + messageType
        ));
    }

    private void validateParticipantActive(String bankCode, String role) {
        Integer activeCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM participants
                WHERE bank_code = ?
                  AND status = 'ACTIVE'
                """,
                Integer.class,
                bankCode
        );

        if (activeCount == null || activeCount == 0) {
            throw new IllegalStateException("Inactive or missing " + role + " participant: " + bankCode);
        }
    }

    private void putIfColumnExists(Map<String, Object> values, String columnName, Object value) {
        if (hasTableColumn("outbox_messages", columnName)) {
            values.put(columnName, value);
        }
    }

    private boolean hasTableColumn(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = ?
                  AND column_name = ?
                """,
                Integer.class,
                tableName,
                columnName
        );

        return count != null && count > 0;
    }

    private boolean hasTable(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = current_schema()
                  AND table_name = ?
                """,
                Integer.class,
                tableName
        );

        return count != null && count > 0;
    }

    private void setXmlPayload(IsoMessageEntity isoMessage, String xml) {
        setValueIfPresent(
                isoMessage,
                xml,
                "messageXml",
                "rawXml",
                "xmlPayload",
                "payload",
                "messagePayload",
                "messageXmlContent",
                "xmlContent",
                "content",
                "isoXml"
        );
    }

    private void setEncryptedPayload(IsoMessageEntity isoMessage, String encryptedPayload) {
        setValueIfPresent(
                isoMessage,
                encryptedPayload,
                "encryptedPayload",
                "encryptedXml",
                "encryptedMessage",
                "encryptedMessageXml",
                "cipherText",
                "ciphertext",
                "encryptedContent",
                "encryptedIsoXml"
        );
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "UNKNOWN";
    }

    private String safePart(String value) {
        if (value == null || value.isBlank()) {
            return "NA";
        }
        return value.trim();
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private Long getLongValue(Object target, String... possiblePropertyNames) {
        for (String propertyName : possiblePropertyNames) {
            Method getter = findGetter(target.getClass(), propertyName);

            if (getter != null) {
                try {
                    Object value = getter.invoke(target);
                    if (value instanceof Number number) {
                        return number.longValue();
                    }
                } catch (Exception e) {
                    throw new IllegalStateException(
                            "Failed to call getter for " + target.getClass().getSimpleName() + "." + propertyName,
                            e
                    );
                }
            }

            Field field = findField(target.getClass(), propertyName);

            if (field != null) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(target);
                    if (value instanceof Number number) {
                        return number.longValue();
                    }
                } catch (Exception e) {
                    throw new IllegalStateException(
                            "Failed to read field " + target.getClass().getSimpleName() + "." + propertyName,
                            e
                    );
                }
            }
        }

        return null;
    }

    private void setEnumOrStringValue(Object target, String propertyName, Enum<?> enumValue) {
        if (enumValue == null) {
            setValue(target, propertyName, null);
            return;
        }

        Class<?> propertyType = findPropertyType(target.getClass(), propertyName);

        if (propertyType == null) {
            return;
        }

        if (String.class.equals(propertyType)) {
            setValue(target, propertyName, enumValue.name());
            return;
        }

        if (propertyType.isEnum()) {
            setValue(target, propertyName, enumValue);
            return;
        }

        setValue(target, propertyName, enumValue.name());
    }

    private void setValueIfPresent(Object target, Object value, String... possiblePropertyNames) {
        for (String propertyName : possiblePropertyNames) {
            if (hasProperty(target.getClass(), propertyName)) {
                setValue(target, propertyName, value);
                return;
            }
        }
    }

    private boolean hasProperty(Class<?> targetClass, String propertyName) {
        return findSetter(targetClass, propertyName) != null || findField(targetClass, propertyName) != null;
    }

    private Class<?> findPropertyType(Class<?> targetClass, String propertyName) {
        Method setter = findSetter(targetClass, propertyName);
        if (setter != null) {
            return setter.getParameterTypes()[0];
        }

        Field field = findField(targetClass, propertyName);
        if (field != null) {
            return field.getType();
        }

        return null;
    }

    private void setValue(Object target, String propertyName, Object value) {
        Method setter = findSetter(target.getClass(), propertyName);

        if (setter != null) {
            try {
                setter.invoke(target, convertValue(value, setter.getParameterTypes()[0]));
                return;
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Failed to call setter for " + target.getClass().getSimpleName() + "." + propertyName,
                        e
                );
            }
        }

        Field field = findField(target.getClass(), propertyName);

        if (field == null) {
            return;
        }

        try {
            field.setAccessible(true);
            field.set(target, convertValue(value, field.getType()));
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to set field " + target.getClass().getSimpleName() + "." + propertyName,
                    e
            );
        }
    }

    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }

        if (targetType.isAssignableFrom(value.getClass())) {
            return value;
        }

        if (String.class.equals(targetType) && value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }

        if (targetType.isEnum() && value instanceof String stringValue) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object enumObject = Enum.valueOf((Class<? extends Enum>) targetType, stringValue);
            return enumObject;
        }

        return value;
    }

    private Method findSetter(Class<?> targetClass, String propertyName) {
        String setterName = "set" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);

        for (Method method : targetClass.getMethods()) {
            if (method.getName().equals(setterName) && method.getParameterCount() == 1) {
                return method;
            }
        }

        return null;
    }

    private Method findGetter(Class<?> targetClass, String propertyName) {
        String suffix = Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
        String getterName = "get" + suffix;

        for (Method method : targetClass.getMethods()) {
            if (method.getName().equals(getterName) && method.getParameterCount() == 0) {
                return method;
            }
        }

        return null;
    }

    private Field findField(Class<?> targetClass, String propertyName) {
        Class<?> current = targetClass;

        while (current != null && !Object.class.equals(current)) {
            try {
                return current.getDeclaredField(propertyName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }

        return null;
    }

    private void validateMandatoryIsoInquiry(Pacs008InboundRequest request) {
        String inquiryRef = request.getInquiryRef();

        if (inquiryRef == null || inquiryRef.isBlank()) {
            throw new IllegalStateException("InquiryRef is required. Send ACMT.023 inquiry before PACS.008 transfer.");
        }

        Map<String, Object> inquiry = jdbcTemplate.query(
                """
                SELECT inquiry_ref,
                       status,
                       source_bank,
                       destination_bank,
                       debtor_account,
                       creditor_account,
                       amount,
                       currency,
                       eligible_for_transfer,
                       expires_at,
                       used_by_transaction_ref
                FROM inquiries
                WHERE inquiry_ref = ?
                ORDER BY business_date DESC
                LIMIT 1
                """,
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }

                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("inquiry_ref", rs.getString("inquiry_ref"));
                    row.put("status", rs.getString("status"));
                    row.put("source_bank_code", rs.getString("source_bank"));
                    row.put("destination_bank_code", rs.getString("destination_bank"));
                    row.put("debtor_account_no", rs.getString("debtor_account"));
                    row.put("creditor_account_no", rs.getString("creditor_account"));
                    row.put("amount", rs.getBigDecimal("amount"));
                    row.put("currency", rs.getString("currency"));
                    row.put("eligible_for_transfer", rs.getObject("eligible_for_transfer"));
                    row.put("expires_at", rs.getObject("expires_at", LocalDateTime.class));
                    row.put("used_by_transfer_ref", rs.getString("used_by_transaction_ref"));
                    return row;
                },
                inquiryRef.trim()
        );

        if (inquiry == null) {
            throw new IllegalStateException("InquiryRef not found: " + inquiryRef);
        }

        String status = stringValue(inquiry.get("status"));
        if (!"ELIGIBLE".equals(status)) {
            throw new IllegalStateException("InquiryRef is not eligible for transfer: " + inquiryRef + ", status=" + status);
        }

        Boolean eligible = booleanValue(inquiry.get("eligible_for_transfer"));
        if (!Boolean.TRUE.equals(eligible)) {
            throw new IllegalStateException("InquiryRef is not eligible for transfer: " + inquiryRef);
        }

        LocalDateTime expiresAt = (LocalDateTime) inquiry.get("expires_at");
        if (expiresAt != null && expiresAt.isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("InquiryRef is expired: " + inquiryRef);
        }

        String usedByTransferRef = stringValue(inquiry.get("used_by_transfer_ref"));
        if (usedByTransferRef != null && !usedByTransferRef.isBlank()) {
            throw new IllegalStateException("InquiryRef has already been used by transfer: " + usedByTransferRef + ", status=USED");
        }

        requireMatch("sourceBank", stringValue(inquiry.get("source_bank_code")), request.getDebtorAgentBic());
        requireMatch("destinationBank", stringValue(inquiry.get("destination_bank_code")), request.getCreditorAgentBic());

        /*
         * Current ACMT.023 local profile verifies the creditor/destination account only.
         * Do not enforce debtorAccount here because the inquiry XML may carry only
         * the account being verified, which maps to PACS.008 creditorAccount.
         */

        /*
         * Creditor account is the main account being verified by ACMT.023 and must match PACS.008.
         */
        requireMatch("creditorAccount", stringValue(inquiry.get("creditor_account_no")), request.getCreditorAccount());

        Object inquiryAmountValue = inquiry.get("amount");
        if (inquiryAmountValue instanceof BigDecimal inquiryAmount && request.getAmount() != null) {
            if (inquiryAmount.compareTo(request.getAmount()) != 0) {
                throw new IllegalStateException(
                        "Inquiry amount does not match PACS.008 amount. inquiry="
                                + inquiryAmount
                                + ", pacs008="
                                + request.getAmount()
                );
            }
        }

        requireMatchIfPresent("currency", stringValue(inquiry.get("currency")), request.getCurrency());
    }

    private void markInquiryUsed(String inquiryRef, String transferRef, LocalDateTime now) {
        int updated = jdbcTemplate.update(
                """
                UPDATE inquiries
                SET status = 'USED',
                    used_by_transaction_ref = ?,
                    updated_at = ?
                WHERE inquiry_ref = ?
                  AND status = 'ELIGIBLE'
                  AND used_by_transaction_ref IS NULL
                """,
                transferRef,
                now,
                inquiryRef
        );

        if (updated != 1) {
            throw new IllegalStateException("Unable to mark InquiryRef as used: " + inquiryRef);
        }

        // Record the ELIGIBLE → USED transition in inquiry_status_history for traceability
        jdbcTemplate.update(
                """
                INSERT INTO inquiry_status_history (inquiry_ref, status, reason_code, created_at)
                VALUES (?, 'USED', NULL, ?)
                """,
                inquiryRef, now
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("inquiryRef", inquiryRef);
        payload.put("transferRef", transferRef);
        payload.put("status", "USED");
        payload.put("usedAt", now);

        auditLogService.log(
                "ISO_INQUIRY_USED_BY_TRANSFER",
                "INQUIRY",
                inquiryRef,
                ISO_INBOUND_CHANNEL_ID,
                payload
        );
    }

    private void requireMatch(String fieldName, String expected, String actual) {
        if (expected == null || expected.isBlank()) {
            throw new IllegalStateException("Inquiry " + fieldName + " is missing");
        }

        if (actual == null || actual.isBlank()) {
            throw new IllegalStateException("PACS.008 " + fieldName + " is missing");
        }

        if (!expected.trim().equals(actual.trim())) {
            throw new IllegalStateException(
                    "Inquiry " + fieldName + " does not match PACS.008. expected="
                            + expected
                            + ", actual="
                            + actual
            );
        }
    }

    private void requireMatchIfPresent(String fieldName, String expected, String actual) {
        if (expected == null || expected.isBlank()) {
            return;
        }

        if (actual == null || actual.isBlank()) {
            throw new IllegalStateException("PACS.008 " + fieldName + " is missing");
        }

        if (!expected.trim().equals(actual.trim())) {
            throw new IllegalStateException(
                    "Inquiry " + fieldName + " does not match PACS.008. expected="
                            + expected
                            + ", actual="
                            + actual
            );
        }
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }

        return String.valueOf(value);
    }

    private Boolean booleanValue(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }

        if (value instanceof Number numberValue) {
            return numberValue.intValue() != 0;
        }

        return Boolean.parseBoolean(String.valueOf(value));
    }

    private record RouteResolution(
            String routeCode,
            String connectorName
    ) {
    }
}
