package com.example.switching.connector;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.example.switching.connector.entity.ConnectorConfigEntity;
import com.example.switching.connector.service.ConnectorConfigService;
import com.example.switching.outbox.dto.BankDispatchResult;
import com.example.switching.outbox.dto.BankIsoDispatchResponse;
import com.example.switching.outbox.dto.DispatchIsoMessageCommand;
import com.example.switching.outbox.dto.DispatchTransferCommand;

/**
 * Generic MQ connector.
 * <p>
 * Skeleton implementation that validates configuration and delegates to the
 * appropriate MQ broker client. A concrete MQ library (RabbitMQ, Kafka,
 * IBM MQ, etc.) must be added to the classpath and wired in
 * {@link #publishAndAwaitReply} before this connector can send real messages.
 * <p>
 * Until a broker client is configured, every dispatch returns a TRANSIENT
 * failure (error code contains "CONNECTION") so the FPRE outbox will retry
 * rather than auto-reverse the transfer.
 * <p>
 * To activate:
 * <ol>
 *   <li>Add the broker client dependency to pom.xml (e.g. spring-boot-starter-amqp)</li>
 *   <li>Set {@code connector_configs.endpoint_url} to the broker URI
 *       (e.g. {@code amqp://host:5672/queue-name})</li>
 *   <li>Implement {@link #publishAndAwaitReply} with the real broker client</li>
 * </ol>
 */
@Component
public class GenericMqConnector implements BankConnector {

    private static final Logger log = LoggerFactory.getLogger(GenericMqConnector.class);

    private final ConnectorConfigService connectorConfigService;

    public GenericMqConnector(ConnectorConfigService connectorConfigService) {
        this.connectorConfigService = connectorConfigService;
    }

    @Override
    public BankDispatchResult dispatch(DispatchTransferCommand command) {
        return BankDispatchResult.failed("MQ-NOT-SUPPORTED",
                "GenericMqConnector does not support non-ISO dispatch");
    }

    @Override
    public BankDispatchResult dispatchIsoMessage(DispatchIsoMessageCommand command) {
        BankIsoDispatchResponse response = dispatchIsoMessageWithPacs002(command);
        if (response.success()) {
            return new BankDispatchResult(true, response.externalReference(),
                    response.responseMessage(), null, null);
        }
        return new BankDispatchResult(false, response.externalReference(), null,
                response.responseCode(), response.responseMessage());
    }

    @Override
    public BankIsoDispatchResponse dispatchIsoMessageWithPacs002(DispatchIsoMessageCommand command) {
        if (command == null) {
            return transient_("MQ-CONNECTION-NULL-COMMAND", "command is null", null);
        }
        if (!StringUtils.hasText(command.encryptedPayload())) {
            return transient_("MQ-CONNECTION-NO-PAYLOAD", "encryptedPayload is required",
                    command.transferRef());
        }

        ConnectorConfigEntity config = connectorConfigService.resolveForDispatch(
                command.connectorName(), command.destinationBank());

        if (!config.enabled()) {
            return transient_("MQ-CONNECTION-DISABLED",
                    "Connector disabled: " + config.getConnectorName(), command.transferRef());
        }
        if (!StringUtils.hasText(config.getEndpointUrl())) {
            log.warn("MQ connector endpoint_url not configured: connectorName={} destinationBank={}",
                    config.getConnectorName(), command.destinationBank());
            return transient_("MQ-CONNECTION-NOT-CONFIGURED",
                    "endpoint_url not set for MQ connector: " + config.getConnectorName(),
                    command.transferRef());
        }

        try {
            return publishAndAwaitReply(command, config);
        } catch (Exception ex) {
            log.error("MQ dispatch failed: transferRef={} connector={} error={}",
                    command.transferRef(), config.getConnectorName(), ex.getMessage());
            return transient_("MQ-CONNECTION-ERROR", "CONNECTION: " + ex.getMessage(),
                    command.transferRef());
        }
    }

    /**
     * Publishes the PACS.008 payload to the broker and waits for the PACS.002 reply.
     * <p>
     * Replace the body with the real broker client logic, for example:
     * <pre>
     *   rabbitTemplate.convertAndSend(queueName, command.encryptedPayload(),
     *       msg -> { msg.getMessageProperties().setCorrelationId(command.transferRef()); return msg; });
     *   String pacs002Xml = (String) rabbitTemplate.receiveAndConvert(replyQueue, timeoutMs);
     *   return parsePacs002(pacs002Xml, command.transferRef());
     * </pre>
     */
    protected BankIsoDispatchResponse publishAndAwaitReply(DispatchIsoMessageCommand command,
                                                            ConnectorConfigEntity config) {
        // No broker client wired yet — return TRANSIENT so FPRE retries later
        log.warn("MQ publishAndAwaitReply not implemented: connectorName={} endpoint={}",
                config.getConnectorName(), config.getEndpointUrl());
        return transient_("MQ-CONNECTION-NOT-IMPLEMENTED",
                "CONNECTION: MQ broker client not wired — add broker dependency and implement publishAndAwaitReply",
                command.transferRef());
    }

    private BankIsoDispatchResponse transient_(String code, String message, String transferRef) {
        return new BankIsoDispatchResponse(false, code, message,
                transferRef != null ? externalRef() : null, null, "RJCT");
    }

    private String externalRef() {
        return "MQ-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
