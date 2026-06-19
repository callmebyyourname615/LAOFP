package com.example.switching.webhook.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.example.switching.webhook.security.WebhookEndpointPolicy;
import com.example.switching.webhook.security.WebhookEndpointPolicyProperties;

import com.example.switching.security.signing.HmacSignatureVerifier;
import com.sun.net.httpserver.HttpServer;

class WebhookHttpSenderTest {

    @Test
    void rotationGraceEmitsCurrentAndPreviousSignatures() throws Exception {
        AtomicReference<String> current = new AtomicReference<>();
        AtomicReference<String> previous = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            current.set(exchange.getRequestHeaders().getFirst("X-Webhook-Signature"));
            previous.set(exchange.getRequestHeaders().getFirst("X-Webhook-Signature-Previous"));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();

        try {
            WebhookEndpointPolicyProperties policyProperties = new WebhookEndpointPolicyProperties();
            policyProperties.setRequireHttps(false);
            policyProperties.setAllowPrivateAddresses(true);
            policyProperties.setAllowedPorts(java.util.Set.of(server.getAddress().getPort()));
            WebhookHttpSender sender = new WebhookHttpSender(
                    HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
                    new WebhookEndpointPolicy(policyProperties));
            String payload = "{\"ok\":true}";
            String currentSecret = "current-secret-value-with-at-least-32-characters";
            String previousSecret = "previous-secret-value-with-at-least-32-characters";
            int status = sender.send(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/hook",
                    "TEST.PING",
                    "delivery-1",
                    payload,
                    currentSecret,
                    previousSecret);

            assertTrue(status >= 200 && status < 300);
            assertNotNull(current.get());
            assertNotNull(previous.get());
            assertEquals(
                    "sha256=" + HmacSignatureVerifier.signHex(payload, currentSecret),
                    current.get());
            assertEquals(
                    "sha256=" + HmacSignatureVerifier.signHex(payload, previousSecret),
                    previous.get());
        } finally {
            server.stop(0);
        }
    }
}
