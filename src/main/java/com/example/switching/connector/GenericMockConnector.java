package com.example.switching.connector;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.example.switching.connector.entity.ConnectorConfigEntity;
import com.example.switching.connector.service.ConnectorConfigService;
import com.example.switching.iso.mapper.Pacs002XmlBuilder;
import com.example.switching.outbox.dto.BankDispatchResult;
import com.example.switching.outbox.dto.BankIsoDispatchResponse;
import com.example.switching.outbox.dto.DispatchIsoMessageCommand;
import com.example.switching.outbox.dto.DispatchTransferCommand;

/**
 * Generic MOCK connector.
 * <p>
 * Handles all connectors whose connector_configs.connector_type = MOCK.
 * Behaviour is driven entirely by {@link ConnectorConfigEntity}:
 * <ul>
 *   <li>enabled = false  → reject without PACS.002 (503)</li>
 *   <li>force_reject = true → reject with PACS.002 RJCT using configured reason</li>
 *   <li>otherwise         → accept with PACS.002 ACSC</li>
 * </ul>
 * This single class replaces the previous per-bank MockBankXConnector pattern
 * and works for any number of member banks as long as their connector_type is MOCK.
 */
@Component
public class GenericMockConnector implements BankConnector {

    private final Pacs002XmlBuilder pacs002XmlBuilder;
    private final ConnectorConfigService connectorConfigService;

    public GenericMockConnector(
            Pacs002XmlBuilder pacs002XmlBuilder,
            ConnectorConfigService connectorConfigService) {
        this.pacs002XmlBuilder = pacs002XmlBuilder;
        this.connectorConfigService = connectorConfigService;
    }

    @Override
    public BankDispatchResult dispatch(DispatchTransferCommand command) {
        return new BankDispatchResult(
                true,
                externalReference(),
                "MOCK_TRANSFER_ACCEPTED",
                null,
                null);
    }

    @Override
    public BankDispatchResult dispatchIsoMessage(DispatchIsoMessageCommand command) {
        BankIsoDispatchResponse response = dispatchIsoMessageWithPacs002(command);

        if (response.success()) {
            return new BankDispatchResult(
                    true,
                    response.externalReference(),
                    response.responseMessage(),
                    null,
                    null);
        }

        return new BankDispatchResult(
                false,
                response.externalReference(),
                null,
                response.responseCode(),
                response.responseMessage());
    }

    @Override
    public BankIsoDispatchResponse dispatchIsoMessageWithPacs002(DispatchIsoMessageCommand command) {
        if (command == null) {
            return rejectedWithoutPacs002("BANK-400", "DispatchIsoMessageCommand is null", null);
        }

        if (!StringUtils.hasText(command.transferRef())) {
            return rejectedWithoutPacs002("BANK-400", "transferRef is required", null);
        }

        if (command.isoMessageId() == null) {
            return rejectedWithoutPacs002("BANK-400", "isoMessageId is required", command.transferRef());
        }

        if (!StringUtils.hasText(command.destinationBank())) {
            return rejectedWithoutPacs002("BANK-400", "destinationBank is required", command.transferRef());
        }

        if (!StringUtils.hasText(command.encryptedPayload())) {
            return rejectedWithoutPacs002("BANK-400", "encryptedPayload is required", command.transferRef());
        }

        ConnectorConfigEntity connectorConfig = connectorConfigService.resolveForDispatch(
                command.connectorName(),
                command.destinationBank());

        if (!connectorConfig.enabled()) {
            return rejectedWithoutPacs002(
                    "BANK-503",
                    "Connector is disabled: " + connectorConfig.getConnectorName(),
                    command.transferRef());
        }

        if (connectorConfig.forceReject()) {
            String reasonCode = StringUtils.hasText(connectorConfig.getRejectReasonCode())
                    ? connectorConfig.getRejectReasonCode()
                    : "AC01";

            String reasonMessage = StringUtils.hasText(connectorConfig.getRejectReasonMessage())
                    ? connectorConfig.getRejectReasonMessage()
                    : "Mock connector rejected transfer";

            String pacs002Xml = pacs002XmlBuilder.buildRejectedResponse(
                    command.messageId(),
                    command.endToEndId(),
                    command.transferRef(),
                    reasonCode,
                    reasonMessage);

            return new BankIsoDispatchResponse(
                    false,
                    "PACS002-RJCT",
                    "MOCK_CONNECTOR_REJECTED_AND_RETURNED_PACS002",
                    externalReference(),
                    pacs002Xml,
                    "RJCT");
        }

        String pacs002Xml = pacs002XmlBuilder.buildAcceptedResponse(
                command.messageId(),
                command.endToEndId(),
                command.transferRef());

        return new BankIsoDispatchResponse(
                true,
                "00",
                "MOCK_CONNECTOR_ACCEPTED_AND_RETURNED_PACS002",
                externalReference(),
                pacs002Xml,
                "ACSC");
    }

    private BankIsoDispatchResponse rejectedWithoutPacs002(
            String responseCode,
            String responseMessage,
            String transferRef) {
        return new BankIsoDispatchResponse(
                false,
                responseCode,
                responseMessage,
                transferRef == null ? null : externalReference(),
                null,
                "RJCT");
    }

    private String externalReference() {
        return "MOCK-" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase();
    }
}
