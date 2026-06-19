package com.example.switching.webhook.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public class WebhookRegisterRequest {

    @NotBlank(message = "url is required")
    @Size(max = 500)
    private String url;

    /**
     * Event types to subscribe to. Use {@code ["*"]} for all events.
     * Standard events: TRANSFER.INITIATED, TRANSFER.SETTLED, TRANSFER.REJECTED,
     * TRANSFER.RETRY_SCHEDULED, TRANSFER.BLOCKED, TEST.PING
     */
    @NotEmpty(message = "eventTypes must have at least one entry")
    private List<String> eventTypes;

    /**
     * PSP-chosen signing secret. Used by the switching server to compute
     * {@code X-Webhook-Signature: sha256=<HMAC>} on every delivery.
     * Min 32 characters.
     */
    @NotBlank(message = "signingSecret is required")
    @Size(min = 32, max = 256, message = "signingSecret must be 32–256 characters")
    private String signingSecret;

    public String getUrl()                         { return url; }
    public void setUrl(String url)                 { this.url = url; }
    public List<String> getEventTypes()            { return eventTypes; }
    public void setEventTypes(List<String> types)  { this.eventTypes = types; }
    public String getSigningSecret()               { return signingSecret; }
    public void setSigningSecret(String s)         { this.signingSecret = s; }
}
