package com.example.switching.webhook.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class WebhookSecretRotateRequest {

    @NotBlank(message = "signingSecret is required")
    @Size(min = 32, max = 256, message = "signingSecret must be 32–256 characters")
    private String signingSecret;

    /** Optional overlap period in minutes; defaults to the server policy. */
    @Min(value = 1, message = "graceMinutes must be at least 1")
    @Max(value = 10080, message = "graceMinutes must not exceed 10080")
    private Integer graceMinutes;

    public String getSigningSecret() {
        return signingSecret;
    }

    public void setSigningSecret(String signingSecret) {
        this.signingSecret = signingSecret;
    }

    public Integer getGraceMinutes() {
        return graceMinutes;
    }

    public void setGraceMinutes(Integer graceMinutes) {
        this.graceMinutes = graceMinutes;
    }
}
