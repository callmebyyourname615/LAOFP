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

/** NAPAS (Vietnam National Payment Corporation) adapter. */
@Component
public class NapasAdapter implements CrossBorderNetworkAdapter {

    private static final Logger log = LoggerFactory.getLogger(NapasAdapter.class);

    private final CrossBorderProperties props;
    private final HttpClient            httpClient;
    private final ObjectMapper          mapper;

    public NapasAdapter(CrossBorderProperties props, ObjectMapper mapper) {
        this.props      = props;
        this.mapper     = mapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(props.getAdapterTimeoutSeconds()))
                .build();
    }

    @Override public String targetNetwork() { return "NAPAS"; }

    @Override
    public String send(CrossBorderInitiateRequest request, FxQuoteEntity quote, Long cbId) {
        try {
            String body = """
                    {"cbId":%d,"vndAmount":"%s","receiverAccount":"%s","receiverName":"%s"}
                    """.formatted(cbId, quote.getDestAmount(), request.beneficiaryAccount(), request.beneficiaryName()).strip();

            HttpResponse<String> resp = httpClient.send(
                    HttpRequest.newBuilder(URI.create(props.getNapasUrl() + "/transfer"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                            .timeout(Duration.ofSeconds(props.getAdapterTimeoutSeconds()))
                            .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new CorridorNotAvailableException("NAPAS HTTP " + resp.statusCode());
            }
            JsonNode node = mapper.readTree(resp.body());
            return node.path("referenceId").asText("NAPAS-" + cbId + "-" + System.nanoTime());
        } catch (CorridorNotAvailableException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("NAPAS adapter error for cbId={}: {}", cbId, ex.getMessage());
            throw new CorridorNotAvailableException("NAPAS unreachable");
        }
    }
}
