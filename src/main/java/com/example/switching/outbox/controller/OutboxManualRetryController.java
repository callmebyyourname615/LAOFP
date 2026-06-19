package com.example.switching.outbox.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.outbox.dto.OutboxManualRetryResponse;
import com.example.switching.outbox.service.OutboxManualRetryService;

@RestController
public class OutboxManualRetryController {

    private final OutboxManualRetryService outboxManualRetryService;

    public OutboxManualRetryController(OutboxManualRetryService outboxManualRetryService) {
        this.outboxManualRetryService = outboxManualRetryService;
    }

    @PostMapping("/api/outbox-events/{outboxEventId}/retry")
    public ResponseEntity<OutboxManualRetryResponse> retryOutboxEvent(
            @PathVariable("outboxEventId") Long outboxEventId) {

        OutboxManualRetryResponse response = outboxManualRetryService.retry(outboxEventId);
        return ResponseEntity.ok(response);
    }
}