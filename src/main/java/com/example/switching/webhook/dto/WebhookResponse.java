package com.example.switching.webhook.dto;

import java.time.LocalDateTime;
import java.util.List;

public record WebhookResponse(
        String       webhookId,
        String       pspId,
        String       url,
        List<String> eventTypes,
        String       status,
        int          failedDeliveries,
        LocalDateTime lastDeliveredAt,
        LocalDateTime createdAt
) {}
