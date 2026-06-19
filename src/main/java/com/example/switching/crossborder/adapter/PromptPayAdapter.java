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

/** PromptPay (Thailand) cross-border adapter. */
@Component
public class PromptPayAdapter implements CrossBorderNetworkAdapter {

    private static final Logger log = LoggerFactory.getLogger(PromptPayAdapter.class);

    private final CrossBorderProperties props;
    private final HttpClient            httpClient;
    private final ObjectMapper          mapper;

    public PromptPayAdapter(CrossBorderProperties props, ObjectMapper mapper) {
        this.props      = props;
        this.mapper     = mapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(props.getAdapterTimeoutSeconds()))
                .build();
    }

    @Override public String targetNetwork() { return "PROMPTPAY"; }

    @Override
    public String send(CrossBorderInitiateRequest request, FxQuoteEntity quote, Long cbId) {
        String body = buildBody(request, quote, cbId);
        try {
            HttpResponse<String> resp = httpClient.send(
                    HttpRequest.newBuilder(URI.create(props.getPromptpayUrl() + "/transfer"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                            .timeout(Duration.ofSeconds(props.getAdapterTimeoutSeconds()))
                            .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new CorridorNotAvailableException("PromptPay HTTP " + resp.statusCode());
            }
            JsonNode node = mapper.readTree(resp.body());
            return node.path("transactionId").asText("PP-" + cbId + "-" + System.nanoTime());
        } catch (CorridorNotAvailableException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("PromptPay adapter error for cbId={}: {}", cbId, ex.getMessage());
            throw new CorridorNotAvailableException("PromptPay unreachable");
        }
    }

    private String buildBody(CrossBorderInitiateRequest r, FxQuoteEntity q, Long cbId) {
        return """
               {"cbId":%d,"amount":"%s","destCurrency":"%s","destAmount":"%s",
                "beneficiaryAccount":"%s","beneficiaryName":"%s","purposeCode":"%s"}
               """.formatted(cbId, q.getSourceAmount(), q.getDestCurrency(), q.getDestAmount(),
                r.beneficiaryAccount(), r.beneficiaryName(),
                r.purposeCode() != null ? r.purposeCode() : "").strip();
    }
}
