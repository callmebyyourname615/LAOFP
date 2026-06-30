package com.example.switching.outbox.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.example.switching.audit.service.AuditLogService;
import com.example.switching.connector.BankConnector;
import com.example.switching.connector.registry.ConnectorRegistry;
import com.example.switching.iso.dto.Pacs002ParseResult;
import com.example.switching.iso.entity.IsoMessageEntity;
import com.example.switching.iso.enums.IsoSecurityStatus;
import com.example.switching.iso.exception.IsoMessageInvalidStateException;
import com.example.switching.iso.exception.IsoMessageNotFoundException;
import com.example.switching.iso.parser.Pacs002Parser;
import com.example.switching.iso.repository.IsoMessageRepository;
import com.example.switching.iso.service.InboundPacs002MessageService;
import com.example.switching.outbox.dto.BankDispatchResult;
import com.example.switching.outbox.dto.BankIsoDispatchResponse;
import com.example.switching.outbox.dto.DispatchIsoMessageCommand;
import com.example.switching.outbox.dto.StatusEnquiryCommand;
import com.example.switching.outbox.dto.StatusEnquiryResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class OutboxIsoMessageDispatchService {

    private static final String ENTITY_TYPE = "TRANSFER";
    private static final String SOURCE_SYSTEM = "WORKER";

    private final ObjectMapper objectMapper;
    private final IsoMessageRepository isoMessageRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ConnectorRegistry connectorRegistry;
    private final Pacs002Parser pacs002Parser;
    private final InboundPacs002MessageService inboundPacs002MessageService;
    private final AuditLogService auditLogService;

    public OutboxIsoMessageDispatchService(
            ObjectMapper objectMapper,
            IsoMessageRepository isoMessageRepository,
            JdbcTemplate jdbcTemplate,
            ConnectorRegistry connectorRegistry,
            Pacs002Parser pacs002Parser,
            InboundPacs002MessageService inboundPacs002MessageService,
            AuditLogService auditLogService) {
        this.objectMapper = objectMapper;
        this.isoMessageRepository = isoMessageRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.connectorRegistry = connectorRegistry;
        this.pacs002Parser = pacs002Parser;
        this.inboundPacs002MessageService = inboundPacs002MessageService;
        this.auditLogService = auditLogService;
    }

    public BankDispatchResult dispatchEncryptedIsoMessage(String outboxPayload) {
        try {
            JsonNode payload = objectMapper.readTree(outboxPayload);

            String transferRef = requiredText(payload, "transferRef");
            Long isoMessageId = requiredLong(payload, "isoMessageId");
            String sourceBank = requiredText(payload, "sourceBank");
            String destinationBank = requiredText(payload, "destinationBank");

            /*
             * IMPORTANT:
             *
             * Old outbox payloads may not contain messageType.
             * Do not use requiredText(payload, "messageType") here.
             *
             * We load the outbound ISO message by isoMessageId first,
             * then fallback messageType from iso_messages.message_type.
             */
            String messageType = optionalText(payload, "messageType");

            String routeCode = requiredText(payload, "routeCode");
            String connectorName = requiredText(payload, "connectorName");

            IsoMessageEntity outboundPacs008 = isoMessageRepository.findById(isoMessageId)
                    .orElseThrow(() -> new IsoMessageNotFoundException(String.valueOf(isoMessageId)));
            hydratePayloads(outboundPacs008);

            if (!StringUtils.hasText(messageType) && outboundPacs008.getMessageType() != null) {
                messageType = String.valueOf(outboundPacs008.getMessageType());
            }

            if (!StringUtils.hasText(messageType)) {
                throw new IsoMessageInvalidStateException(
                        "messageType is missing from outbox payload and iso_messages. isoMessageId="
                                + isoMessageId
                                + ", transferRef="
                                + transferRef);
            }

            validateOutboundPacs008(outboundPacs008, transferRef);

            logIsoDispatchStarted(
                    transferRef,
                    outboundPacs008,
                    sourceBank,
                    destinationBank,
                    messageType,
                    routeCode,
                    connectorName);

            DispatchIsoMessageCommand command = new DispatchIsoMessageCommand(
                    transferRef,
                    outboundPacs008.getId(),
                    outboundPacs008.getMessageId(),
                    outboundPacs008.getEndToEndId(),
                    messageType,
                    sourceBank,
                    destinationBank,
                    connectorName,
                    outboundPacs008.getEncryptedPayload());

            BankConnector connector = connectorRegistry.resolve(connectorName);
            BankIsoDispatchResponse bankResponse = connector.dispatchIsoMessageWithPacs002(command);

            if (bankResponse == null) {
                return new BankDispatchResult(
                        false,
                        null,
                        null,
                        "PACS002-NULL",
                        "BankConnector returned null PACS.002 response");
            }

            logPacs002ResponseReceived(
                    transferRef,
                    outboundPacs008,
                    routeCode,
                    connectorName,
                    bankResponse);

            if (StringUtils.hasText(bankResponse.pacs002Xml())) {
                Pacs002ParseResult pacs002 = pacs002Parser.parse(bankResponse.pacs002Xml());

                IsoMessageEntity inboundPacs002 = inboundPacs002MessageService.saveInboundPacs002(
                        outboundPacs008,
                        pacs002,
                        bankResponse.pacs002Xml());

                logPacs002InboundSaved(
                        transferRef,
                        inboundPacs002,
                        pacs002,
                        routeCode,
                        connectorName,
                        bankResponse.externalReference());

                logPacs002Parsed(
                        transferRef,
                        inboundPacs002,
                        pacs002,
                        routeCode,
                        connectorName);

                if (pacs002.accepted()) {
                    return new BankDispatchResult(
                            true,
                            bankResponse.externalReference(),
                            "PACS.002 accepted with TxSts=" + pacs002.transactionStatus(),
                            null,
                            null);
                }

                if (pacs002.rejected()) {
                    return new BankDispatchResult(
                            false,
                            bankResponse.externalReference(),
                            null,
                            "PACS002-RJCT",
                            "PACS.002 rejected. reasonCode="
                                    + pacs002.reasonCode()
                                    + ", reasonMessage="
                                    + pacs002.reasonMessage());
                }

                return new BankDispatchResult(
                        false,
                        bankResponse.externalReference(),
                        null,
                        "PACS002-UNKNOWN",
                        "Unsupported PACS.002 TxSts=" + pacs002.transactionStatus());
            }

            if (bankResponse.success()) {
                return new BankDispatchResult(
                        false,
                        bankResponse.externalReference(),
                        null,
                        "PACS002-001",
                        "Bank response success but PACS.002 XML is empty");
            }

            return new BankDispatchResult(
                    false,
                    bankResponse.externalReference(),
                    null,
                    bankResponse.responseCode(),
                    bankResponse.responseMessage());

        } catch (IsoMessageNotFoundException | IsoMessageInvalidStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Failed to dispatch encrypted ISO message from outbox payload",
                    ex);
        }
    }

    public StatusEnquiryResult enquireDestinationStatus(String outboxPayload) {
        try {
            JsonNode payload = objectMapper.readTree(outboxPayload);

            String transferRef = requiredText(payload, "transferRef");
            Long isoMessageId = requiredLong(payload, "isoMessageId");
            String sourceBank = requiredText(payload, "sourceBank");
            String destinationBank = requiredText(payload, "destinationBank");
            String routeCode = requiredText(payload, "routeCode");
            String connectorName = requiredText(payload, "connectorName");

            IsoMessageEntity outboundPacs008 = isoMessageRepository.findById(isoMessageId)
                    .orElseThrow(() -> new IsoMessageNotFoundException(String.valueOf(isoMessageId)));
            hydratePayloads(outboundPacs008);

            String messageType = optionalText(payload, "messageType");
            if (!StringUtils.hasText(messageType) && outboundPacs008.getMessageType() != null) {
                messageType = String.valueOf(outboundPacs008.getMessageType());
            }

            validateOutboundPacs008(outboundPacs008, transferRef);

            Map<String, Object> auditPayload = new LinkedHashMap<>();
            auditPayload.put("transferRef", transferRef);
            auditPayload.put("isoMessageId", isoMessageId);
            auditPayload.put("messageId", outboundPacs008.getMessageId());
            auditPayload.put("endToEndId", outboundPacs008.getEndToEndId());
            auditPayload.put("sourceBank", sourceBank);
            auditPayload.put("destinationBank", destinationBank);
            auditPayload.put("routeCode", routeCode);
            auditPayload.put("connectorName", connectorName);
            auditLogService.log(
                    "STATUS_ENQUIRY_STARTED",
                    ENTITY_TYPE,
                    transferRef,
                    SOURCE_SYSTEM,
                    auditPayload);

            BankConnector connector = connectorRegistry.resolve(connectorName);
            StatusEnquiryResult result = connector.enquireStatus(new StatusEnquiryCommand(
                    transferRef,
                    outboundPacs008.getId(),
                    outboundPacs008.getMessageId(),
                    outboundPacs008.getEndToEndId(),
                    messageType,
                    sourceBank,
                    destinationBank,
                    connectorName));

            Map<String, Object> resultPayload = new LinkedHashMap<>(auditPayload);
            resultPayload.put("status", result.status().name());
            resultPayload.put("responseCode", result.responseCode());
            resultPayload.put("responseMessage", result.responseMessage());
            resultPayload.put("externalReference", result.externalReference());
            auditLogService.log(
                    "STATUS_ENQUIRY_COMPLETED",
                    ENTITY_TYPE,
                    transferRef,
                    SOURCE_SYSTEM,
                    resultPayload);

            return result;
        } catch (Exception ex) {
            return StatusEnquiryResult.unknown(
                    "STATUS-ENQUIRY-FAILED",
                    ex.getMessage());
        }
    }

    private void validateOutboundPacs008(IsoMessageEntity isoMessage, String transferRef) {
        if (!StringUtils.hasText(isoMessage.getTransferRef())) {
            throw new IsoMessageInvalidStateException(
                    "ISO message transferRef is empty. isoMessageId=" + isoMessage.getId());
        }

        if (!isoMessage.getTransferRef().equals(transferRef)) {
            throw new IsoMessageInvalidStateException(
                    "ISO message transferRef does not match outbox transferRef. isoMessageId="
                            + isoMessage.getId()
                            + ", isoTransferRef="
                            + isoMessage.getTransferRef()
                            + ", outboxTransferRef="
                            + transferRef);
        }

        if (isoMessage.getSecurityStatus() != IsoSecurityStatus.ENCRYPTED) {
            throw new IsoMessageInvalidStateException(
                    "ISO message must be ENCRYPTED before dispatch. isoMessageId="
                            + isoMessage.getId()
                            + ", securityStatus="
                            + isoMessage.getSecurityStatus());
        }

        if (!StringUtils.hasText(isoMessage.getEncryptedPayload())) {
            throw new IsoMessageInvalidStateException(
                    "ISO encryptedPayload is empty. isoMessageId=" + isoMessage.getId());
        }
    }

    private void hydratePayloads(IsoMessageEntity isoMessage) {
        if (StringUtils.hasText(isoMessage.getEncryptedPayload())
                || !hasTable("iso_message_payloads")) {
            return;
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT plain_payload, encrypted_payload
                FROM iso_message_payloads
                WHERE iso_message_id = ?
                ORDER BY business_date DESC, id DESC
                LIMIT 1
                """,
                isoMessage.getId()
        );

        if (rows.isEmpty()) {
            return;
        }

        Object plainPayload = rows.get(0).get("plain_payload");
        Object encryptedPayload = rows.get(0).get("encrypted_payload");

        if (plainPayload instanceof String value) {
            isoMessage.setPlainPayload(value);
        }
        if (encryptedPayload instanceof String value) {
            isoMessage.setEncryptedPayload(value);
        }
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

    private void logIsoDispatchStarted(
            String transferRef,
            IsoMessageEntity outboundPacs008,
            String sourceBank,
            String destinationBank,
            String messageType,
            String routeCode,
            String connectorName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transferRef", transferRef);
        payload.put("outboundIsoMessageId", outboundPacs008.getId());
        payload.put("messageType", messageType);
        payload.put("direction", String.valueOf(outboundPacs008.getDirection()));
        payload.put("messageId", outboundPacs008.getMessageId());
        payload.put("endToEndId", outboundPacs008.getEndToEndId());
        payload.put("securityStatus", String.valueOf(outboundPacs008.getSecurityStatus()));
        payload.put("sourceBank", sourceBank);
        payload.put("destinationBank", destinationBank);
        payload.put("routeCode", routeCode);
        payload.put("connectorName", connectorName);
        payload.put("encryptedPayloadPresent", StringUtils.hasText(outboundPacs008.getEncryptedPayload()));

        auditLogService.log(
                "OUTBOX_ISO_DISPATCH_STARTED",
                ENTITY_TYPE,
                transferRef,
                SOURCE_SYSTEM,
                payload);
    }

    private void logPacs002ResponseReceived(
            String transferRef,
            IsoMessageEntity outboundPacs008,
            String routeCode,
            String connectorName,
            BankIsoDispatchResponse bankResponse) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transferRef", transferRef);
        payload.put("outboundIsoMessageId", outboundPacs008.getId());
        payload.put("outboundMessageId", outboundPacs008.getMessageId());
        payload.put("routeCode", routeCode);
        payload.put("connectorName", connectorName);
        payload.put("responseSuccess", bankResponse.success());
        payload.put("responseCode", bankResponse.responseCode());
        payload.put("responseMessage", bankResponse.responseMessage());
        payload.put("externalReference", bankResponse.externalReference());
        payload.put("isoStatusCode", bankResponse.isoStatusCode());
        payload.put("pacs002XmlPresent", StringUtils.hasText(bankResponse.pacs002Xml()));

        auditLogService.log(
                "PACS002_RESPONSE_RECEIVED",
                ENTITY_TYPE,
                transferRef,
                SOURCE_SYSTEM,
                payload);
    }

    private void logPacs002InboundSaved(
            String transferRef,
            IsoMessageEntity inboundPacs002,
            Pacs002ParseResult pacs002,
            String routeCode,
            String connectorName,
            String externalReference) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transferRef", transferRef);
        payload.put("inboundIsoMessageId", inboundPacs002.getId());
        payload.put("messageType", String.valueOf(inboundPacs002.getMessageType()));
        payload.put("direction", String.valueOf(inboundPacs002.getDirection()));
        payload.put("messageId", inboundPacs002.getMessageId());
        payload.put("endToEndId", inboundPacs002.getEndToEndId());
        payload.put("securityStatus", String.valueOf(inboundPacs002.getSecurityStatus()));
        payload.put("txStatus", pacs002.transactionStatus());
        payload.put("originalMessageId", pacs002.originalMessageId());
        payload.put("originalEndToEndId", pacs002.originalEndToEndId());
        payload.put("originalTransactionId", pacs002.originalTransactionId());
        payload.put("routeCode", routeCode);
        payload.put("connectorName", connectorName);
        payload.put("externalReference", externalReference);

        auditLogService.log(
                "PACS002_INBOUND_SAVED",
                ENTITY_TYPE,
                transferRef,
                SOURCE_SYSTEM,
                payload);
    }

    private void logPacs002Parsed(
            String transferRef,
            IsoMessageEntity inboundPacs002,
            Pacs002ParseResult pacs002,
            String routeCode,
            String connectorName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transferRef", transferRef);
        payload.put("inboundIsoMessageId", inboundPacs002.getId());
        payload.put("messageId", pacs002.messageId());
        payload.put("originalMessageId", pacs002.originalMessageId());
        payload.put("originalEndToEndId", pacs002.originalEndToEndId());
        payload.put("originalTransactionId", pacs002.originalTransactionId());
        payload.put("txStatus", pacs002.transactionStatus());
        payload.put("accepted", pacs002.accepted());
        payload.put("rejected", pacs002.rejected());
        payload.put("reasonCode", pacs002.reasonCode());
        payload.put("reasonMessage", pacs002.reasonMessage());
        payload.put("routeCode", routeCode);
        payload.put("connectorName", connectorName);

        auditLogService.log(
                "PACS002_PARSED",
                ENTITY_TYPE,
                transferRef,
                SOURCE_SYSTEM,
                payload);
    }

    private String requiredText(JsonNode node, String fieldName) {
        String value = optionalText(node, fieldName);

        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Missing required field in outbox payload: " + fieldName);
        }

        return value;
    }

    private String optionalText(JsonNode node, String fieldName) {
        if (node == null) {
            return null;
        }

        JsonNode value = node.get(fieldName);

        if (value == null || value.isNull()) {
            return null;
        }

        String text = value.asText();

        if (!StringUtils.hasText(text)) {
            return null;
        }

        return text.trim();
    }

    private Long requiredLong(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);

        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("Missing required field in outbox payload: " + fieldName);
        }

        if (!value.canConvertToLong()) {
            throw new IllegalArgumentException("Invalid long field in outbox payload: " + fieldName);
        }

        return value.asLong();
    }
}
