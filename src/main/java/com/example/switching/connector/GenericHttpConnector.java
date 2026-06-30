package com.example.switching.connector;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.example.switching.connector.entity.ConnectorConfigEntity;
import com.example.switching.connector.service.ConnectorConfigService;
import com.example.switching.iso.dto.Pacs002ParseResult;
import com.example.switching.iso.parser.Pacs002Parser;
import com.example.switching.outbox.dto.BankDispatchResult;
import com.example.switching.outbox.dto.BankIsoDispatchResponse;
import com.example.switching.outbox.dto.DispatchIsoMessageCommand;
import com.example.switching.outbox.dto.DispatchTransferCommand;
import com.example.switching.outbox.dto.StatusEnquiryCommand;
import com.example.switching.outbox.dto.StatusEnquiryResult;

/**
 * Generic HTTP connector.
 * <p>
 * POSTs the encrypted PACS.008 payload to {@code connector_configs.endpoint_url}
 * and parses the PACS.002 XML response.
 * <p>
 * Error classification (drives FPRE retry behaviour):
 * <ul>
 *   <li>Network timeout / refused → code contains "TIMEOUT" → TRANSIENT</li>
 *   <li>HTTP 5xx → code contains "UNAVAILABLE" → TRANSIENT</li>
 *   <li>HTTP 4xx → PERMANENT_BUSINESS</li>
 *   <li>Unparseable / empty response → code contains "AMBIGUOUS" → AMBIGUOUS</li>
 * </ul>
 */
@Component
public class GenericHttpConnector implements BankConnector {

    private final ConnectorConfigService connectorConfigService;
    private final Pacs002Parser pacs002Parser;

    public GenericHttpConnector(ConnectorConfigService connectorConfigService,
                                Pacs002Parser pacs002Parser) {
        this.connectorConfigService = connectorConfigService;
        this.pacs002Parser = pacs002Parser;
    }

    @Override
    public BankDispatchResult dispatch(DispatchTransferCommand command) {
        return BankDispatchResult.failed("HTTP-NOT-SUPPORTED",
                "GenericHttpConnector does not support non-ISO dispatch");
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
            return failure("HTTP-400", "DispatchIsoMessageCommand is null", null);
        }
        if (!StringUtils.hasText(command.encryptedPayload())) {
            return failure("HTTP-400", "encryptedPayload is required", command.transferRef());
        }

        ConnectorConfigEntity config = connectorConfigService.resolveForDispatch(
                command.connectorName(), command.destinationBank());

        if (!config.enabled()) {
            return failure("HTTP-503", "Connector disabled: " + config.getConnectorName(),
                    command.transferRef());
        }
        if (!StringUtils.hasText(config.getEndpointUrl())) {
            return failure("HTTP-MISCONFIGURED",
                    "endpoint_url not configured for connector: " + config.getConnectorName(),
                    command.transferRef());
        }

        RestClient client = RestClient.builder()
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_XML_VALUE)
                .defaultHeader("X-Transfer-Ref", command.transferRef())
                .defaultHeader("X-Message-Id", orEmpty(command.messageId()))
                .defaultHeader("X-Source-Bank", orEmpty(command.sourceBank()))
                .build();

        try {
            String responseBody = client.post()
                    .uri(URI.create(config.getEndpointUrl()))
                    .body(command.encryptedPayload())
                    .retrieve()
                    .onStatus(s -> s.is4xxClientError(), (req, res) -> {
                        throw new RestClientResponseException(
                                "HTTP " + res.getStatusCode().value(),
                                res.getStatusCode().value(),
                                res.getStatusCode().toString(),
                                res.getHeaders(), null, null);
                    })
                    .onStatus(s -> s.is5xxServerError(), (req, res) -> {
                        throw new RestClientResponseException(
                                "HTTP " + res.getStatusCode().value(),
                                res.getStatusCode().value(),
                                res.getStatusCode().toString(),
                                res.getHeaders(), null, null);
                    })
                    .body(String.class);

            return parsePacs002Response(responseBody, command.transferRef());

        } catch (ResourceAccessException ex) {
            String msg = ex.getMessage() != null ? ex.getMessage() : "Network error";
            boolean isTimeout = msg.toLowerCase().contains("timeout")
                    || msg.toLowerCase().contains("timed out");
            return failure(isTimeout ? "HTTP-TIMEOUT" : "HTTP-CONNECTION-REFUSED",
                    "TIMEOUT: " + msg, command.transferRef());

        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status >= 500) {
                return failure("HTTP-UNAVAILABLE-" + status,
                        "UNAVAILABLE: destination returned HTTP " + status, command.transferRef());
            }
            return failure("HTTP-CLIENT-ERROR-" + status,
                    "Client error HTTP " + status, command.transferRef());

        } catch (Exception ex) {
            return failure("HTTP-UNKNOWN", "AMBIGUOUS: " + ex.getMessage(), command.transferRef());
        }
    }

    @Override
    public StatusEnquiryResult enquireStatus(StatusEnquiryCommand command) {
        if (command == null) {
            return StatusEnquiryResult.unknown("HTTP-400", "StatusEnquiryCommand is null");
        }

        ConnectorConfigEntity config = connectorConfigService.resolveForDispatch(
                command.connectorName(), command.destinationBank());

        if (!config.enabled()) {
            return StatusEnquiryResult.unknown(
                    "HTTP-503",
                    "Connector disabled: " + config.getConnectorName());
        }
        if (!StringUtils.hasText(config.getEndpointUrl())) {
            return StatusEnquiryResult.unknown(
                    "HTTP-MISCONFIGURED",
                    "endpoint_url not configured for connector: " + config.getConnectorName());
        }

        String enquiryUrl = config.getEndpointUrl().replaceAll("/+$", "")
                + "/status/" + command.transferRef();

        RestClient client = RestClient.builder()
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-Transfer-Ref", command.transferRef())
                .defaultHeader("X-Message-Id", orEmpty(command.messageId()))
                .defaultHeader("X-End-To-End-Id", orEmpty(command.endToEndId()))
                .build();

        try {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> body = client.get()
                    .uri(URI.create(enquiryUrl))
                    .retrieve()
                    .body(java.util.Map.class);

            String status = body == null ? null : String.valueOf(body.getOrDefault("status", "UNKNOWN"));
            String externalReference = body == null ? null : String.valueOf(body.getOrDefault("externalReference", ""));
            String message = body == null ? null : String.valueOf(body.getOrDefault("message", status));

            return switch (status == null ? "UNKNOWN" : status.toUpperCase()) {
                case "ACCEPTED", "CREDITED", "READY_FOR_SETTLEMENT", "SETTLED", "ACSC", "ACCP" ->
                        StatusEnquiryResult.accepted(externalReference, message);
                case "REJECTED", "RJCT" ->
                        StatusEnquiryResult.rejected(externalReference, "RJCT", message);
                case "NOT_FOUND" ->
                        new StatusEnquiryResult(StatusEnquiryResult.Status.NOT_FOUND,
                                externalReference, "NOT_FOUND", message);
                case "PROCESSING", "PENDING" ->
                        new StatusEnquiryResult(StatusEnquiryResult.Status.PROCESSING,
                                externalReference, "PROCESSING", message);
                default -> StatusEnquiryResult.unknown("HTTP-STATUS-UNKNOWN", message);
            };
        } catch (Exception ex) {
            return StatusEnquiryResult.unknown(
                    "HTTP-STATUS-ENQUIRY-FAILED",
                    ex.getMessage());
        }
    }

    private BankIsoDispatchResponse parsePacs002Response(String body, String transferRef) {
        if (!StringUtils.hasText(body)) {
            return failure("PACS002-EMPTY", "AMBIGUOUS: empty response body", transferRef);
        }
        try {
            Pacs002ParseResult parsed = pacs002Parser.parse(body);
            boolean accepted = "ACSC".equals(parsed.transactionStatus())
                    || "ACCP".equals(parsed.transactionStatus())
                    || "ACSP".equals(parsed.transactionStatus());

            if (accepted) {
                return new BankIsoDispatchResponse(true, "00", "HTTP_CONNECTOR_ACCEPTED",
                        externalRef(), body, parsed.transactionStatus());
            }
            return new BankIsoDispatchResponse(false,
                    StringUtils.hasText(parsed.reasonCode()) ? parsed.reasonCode() : "RJCT",
                    StringUtils.hasText(parsed.reasonMessage()) ? parsed.reasonMessage() : "PACS002-RJCT",
                    externalRef(), body, parsed.transactionStatus());

        } catch (Exception ex) {
            return failure("PACS002-UNKNOWN",
                    "AMBIGUOUS: cannot parse PACS.002 response: " + ex.getMessage(), transferRef);
        }
    }

    private BankIsoDispatchResponse failure(String code, String message, String transferRef) {
        return new BankIsoDispatchResponse(false, code, message,
                transferRef != null ? externalRef() : null, null, "RJCT");
    }

    private String externalRef() {
        return "HTTP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private String orEmpty(String v) {
        return v != null ? v : "";
    }
}
