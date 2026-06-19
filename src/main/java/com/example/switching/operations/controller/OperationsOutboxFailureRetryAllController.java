package com.example.switching.operations.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.operations.dto.OperationsOutboxFailureRetryAllResponse;
import com.example.switching.operations.service.OperationsOutboxFailureRetryAllService;

@RestController
@RequestMapping("/api/operations")
public class OperationsOutboxFailureRetryAllController {

    private final OperationsOutboxFailureRetryAllService retryAllService;

    public OperationsOutboxFailureRetryAllController(
            OperationsOutboxFailureRetryAllService retryAllService
    ) {
        this.retryAllService = retryAllService;
    }

    @PostMapping("/outbox-failures/retry-all")
    public OperationsOutboxFailureRetryAllResponse retryAllFailedOutboxEvents(
            @RequestParam(required = false) Integer limit
    ) {
        return retryAllService.retryAllFailed(limit);
    }
}