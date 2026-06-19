package com.example.switching.crossborder.adapter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.switching.crossborder.config.CrossBorderProperties;
import com.example.switching.crossborder.dto.CrossBorderInitiateRequest;
import com.example.switching.crossborder.entity.FxQuoteEntity;
import com.example.switching.crossborder.exception.CorridorNotAvailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** SWIFT (MT103 / gpi) cross-border adapter. */
@Component
public class SwiftAdapter implements CrossBorderNetworkAdapter {

    private static final Logger log = LoggerFactory.getLogger(SwiftAdapter.class);

    private final CrossBorderProperties props;
    private final HttpClient            httpClient;
    private final ObjectMapper          mapper;

    public SwiftAdapter(CrossBorderProperties props, ObjectMapper mapper) {
        this.props      = props;
        this.mapper     = mapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(props.getAdapterTimeoutSeconds()))
                .build();
    }

    @Override public String targetNetwork() { return "SWIFT"; }

    @Override
    public String send(CrossBorderInitiateRequest request, FxQuoteEntity quote, Long cbId) {
        try {
            String body = """
                    {"cbId":%d,"senderBic":"%s","amount":"%s","currency":"%s",
                     "beneficiaryAccount":"%s","beneficiaryBank":"%s","purposeCode":"%s"}
                    """.formatted(cbId, props.getSwiftBic(), quote.getDestAmount(),
                    quote.getDestCurrency(), request.beneficiaryAccount(),
                    request.beneficiaryBank(),
                    request.purposeCode() != null ? request.purposeCode() : "").strip();

            HttpResponse<String> resp = httpClient.send(
                    HttpRequest.newBuilder(URI.create(props.getSwiftUrl() + "/mt103"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                            .timeout(Duration.ofSeconds(props.getAdapterTimeoutSeconds()))
                            .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new CorridorNotAvailableException("SWIFT HTTP " + resp.statusCode());
            }
            JsonNode node = mapper.readTree(resp.body());
            return node.path("uetr").asText("SWIFT-" + cbId + "-" + System.nanoTime());
        } catch (CorridorNotAvailableException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("SWIFT adapter error for cbId={}: {}", cbId, ex.getMessage());
            throw new CorridorNotAvailableException("SWIFT unreachable");
        }
    }
}
