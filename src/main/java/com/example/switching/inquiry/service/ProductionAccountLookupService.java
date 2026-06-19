package com.example.switching.inquiry.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Production account lookup adapter.
 *
 * <p>The endpoint contract is intentionally small and explicit:
 * {@code POST {baseUrl}/accounts/lookup} with
 * {@code {"destinationBank":"BANK_B","accountNumber":"..."}} and a response
 * containing {@code found}, optional {@code accountName}, and optional
 * {@code errorReason}. If the endpoint is not configured, production startup
 * fails through {@link com.example.switching.config.ProductionStartupValidator}.
 */
@Service
@Profile("prod")
public class ProductionAccountLookupService implements AccountLookupService {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ProductionAccountLookupService(
            @Value("${switching.account-lookup.base-url}") String baseUrl,
            ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public AccountLookupResult lookup(String destinationBank, String accountNumber) {
        if (!StringUtils.hasText(destinationBank)) {
            return AccountLookupResult.notFound("destinationBank is empty");
        }
        if (!StringUtils.hasText(accountNumber)) {
            return AccountLookupResult.notFound("creditorAccount is empty");
        }

        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "destinationBank", destinationBank,
                    "accountNumber", accountNumber));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/accounts/lookup"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                return AccountLookupResult.notFound("account not found");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return AccountLookupResult.notFound("account lookup failed: HTTP " + response.statusCode());
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> body = objectMapper.readValue(response.body(), Map.class);
            boolean found = Boolean.TRUE.equals(body.get("found"));
            if (!found) {
                Object reason = body.get("errorReason");
                return AccountLookupResult.notFound(reason == null ? "account not found" : reason.toString());
            }

            Object name = body.get("accountName");
            return AccountLookupResult.found(name == null ? "VERIFIED ACCOUNT" : name.toString());
        } catch (Exception ex) {
            return AccountLookupResult.notFound("account lookup unavailable: " + ex.getMessage());
        }
    }
}
