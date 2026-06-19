package com.example.switching.operations.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.operations.dto.OperationsOutboxMarkReviewedRequest;
import com.example.switching.operations.dto.OperationsOutboxMarkReviewedResponse;
import com.example.switching.operations.service.OperationsOutboxMarkReviewedService;

@RestController
@RequestMapping("/api/operations")
public class OperationsOutboxMarkReviewedController {

    private final OperationsOutboxMarkReviewedService markReviewedService;

    public OperationsOutboxMarkReviewedController(
            OperationsOutboxMarkReviewedService markReviewedService
    ) {
        this.markReviewedService = markReviewedService;
    }

    @PostMapping("/outbox-events/{id}/mark-reviewed")
    public OperationsOutboxMarkReviewedResponse markReviewed(
            @PathVariable Long id,
            @RequestBody(required = false) OperationsOutboxMarkReviewedRequest request
    ) {
        OperationsOutboxMarkReviewedRequest safeRequest = request == null
                ? new OperationsOutboxMarkReviewedRequest(null, null)
                : request;

        return markReviewedService.markReviewed(id, safeRequest);
    }
}