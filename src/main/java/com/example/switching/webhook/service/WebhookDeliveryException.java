package com.example.switching.webhook.service;

/**
 * Thrown by {@link WebhookHttpSender} on connection / IO / timeout errors.
 * Caught by {@link WebhookDeliveryService} to record the failed attempt and
 * schedule a retry.
 */
public class WebhookDeliveryException extends RuntimeException {

    public WebhookDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
