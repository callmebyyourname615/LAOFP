package com.example.switching.webhook.security;

public class WebhookEndpointRejectedException extends IllegalArgumentException {
    public WebhookEndpointRejectedException(String message) { super(message); }
    public WebhookEndpointRejectedException(String message, Throwable cause) { super(message, cause); }
}
