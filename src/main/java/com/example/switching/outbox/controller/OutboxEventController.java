package com.example.switching.outbox.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.outbox.dto.OutboxEventListResponse;
import com.example.switching.outbox.service.OutboxEventQueryService;

@RestController
public class OutboxEventController {

    private final OutboxEventQueryService outboxEventQueryService;

    public OutboxEventController(OutboxEventQueryService outboxEventQueryService) {
        this.outboxEventQueryService = outboxEventQueryService;
    }

    @GetMapping("/api/outbox-events")
    public ResponseEntity<OutboxEventListResponse> searchOutboxEvents(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "transferRef", required = false) String transferRef,
            @RequestParam(value = "limit", required = false) Integer limit) {

        OutboxEventListResponse response = outboxEventQueryService.search(
                status,
                transferRef,
                limit
        );

        return ResponseEntity.ok(response);
    }
}