package com.example.switching.webhook.service;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.net.http.HttpClient.Redirect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.example.switching.security.signing.HmacSignatureVerifier;
import com.example.switching.webhook.security.WebhookEndpointPolicy;
import com.example.switching.webhook.security.WebhookEndpointPolicyProperties;

/** Low-level HTTP sender for signed webhook payloads. */
@Component
public class WebhookHttpSender {

    private static final Logger log = LoggerFactory.getLogger(WebhookHttpSender.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient httpClient;
    private final WebhookEndpointPolicy endpointPolicy;

    @Autowired
    public WebhookHttpSender(
            WebhookEndpointPolicy endpointPolicy,
            WebhookEndpointPolicyProperties properties) {
        this(buildHttpClient(properties), endpointPolicy);
    }

    private static HttpClient buildHttpClient(WebhookEndpointPolicyProperties properties) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(Redirect.NEVER);
        if (properties.isProxyEnabled()) {
            if (properties.getProxyHost().isBlank()
                    || properties.getProxyPort() < 1
                    || properties.getProxyPort() > 65535) {
                throw new IllegalStateException("Webhook egress proxy configuration is invalid");
            }
            builder.proxy(ProxySelector.of(new InetSocketAddress(
                    properties.getProxyHost(), properties.getProxyPort())));
        }
        return builder.build();
    }

    WebhookHttpSender(HttpClient httpClient, WebhookEndpointPolicy endpointPolicy) {
        this.httpClient = httpClient;
        this.endpointPolicy = endpointPolicy;
    }

    public int send(String url,
                    String eventType,
                    String deliveryId,
                    String payloadJson,
                    String signingSecret) {
        return send(url, eventType, deliveryId, payloadJson, signingSecret, null);
    }

    /**
     * During secret rotation, both current and previous HMACs are emitted. The
     * previous signature is removed automatically when the grace period expires.
     */
    public int send(String url,
                    String eventType,
                    String deliveryId,
                    String payloadJson,
                    String signingSecret,
                    String previousSigningSecret) {
        String signature = computeSignature(payloadJson, signingSecret);

        HttpRequest request;
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(endpointPolicy.validate(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("X-Webhook-Signature", "sha256=" + signature)
                    .header("X-Webhook-Event", eventType)
                    .header("X-Delivery-Id", deliveryId);
            if (previousSigningSecret != null && !previousSigningSecret.isBlank()) {
                builder.header(
                        "X-Webhook-Signature-Previous",
                        "sha256=" + computeSignature(payloadJson, previousSigningSecret));
            }
            request = builder
                    .POST(HttpRequest.BodyPublishers.ofString(payloadJson, StandardCharsets.UTF_8))
                    .build();
        } catch (IllegalArgumentException ex) {
            throw new WebhookDeliveryException("Webhook destination rejected by outbound policy", ex);
        }

        try {
            HttpResponse<Void> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.discarding());
            int statusCode = response.statusCode();
            log.debug("Webhook delivered: host={} event={} deliveryId={} status={}",
                    request.uri().getHost(), eventType, deliveryId, statusCode);
            return statusCode;
        } catch (Exception ex) {
            throw new WebhookDeliveryException(
                    "HTTP error sending webhook to approved destination: " + ex.getClass().getSimpleName(), ex);
        }
    }

    private String computeSignature(String payload, String secret) {
        return HmacSignatureVerifier.signHex(payload, secret);
    }
}
