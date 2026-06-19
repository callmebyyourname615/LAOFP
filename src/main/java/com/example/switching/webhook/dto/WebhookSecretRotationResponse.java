package com.example.switching.webhook.dto;

import java.time.LocalDateTime;

public record WebhookSecretRotationResponse(
        String webhookId,
        int secretVersion,
        LocalDateTime previousSecretExpiresAt,
        String status) {
}
