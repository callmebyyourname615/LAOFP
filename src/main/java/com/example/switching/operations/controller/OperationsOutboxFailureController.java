package com.example.switching.operations.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.operations.dto.OperationsOutboxFailureListResponse;
import com.example.switching.operations.service.OperationsOutboxFailureService;

@RestController
@RequestMapping("/api/operations")
public class OperationsOutboxFailureController {

    private final OperationsOutboxFailureService operationsOutboxFailureService;

    public OperationsOutboxFailureController(
            OperationsOutboxFailureService operationsOutboxFailureService
    ) {
        this.operationsOutboxFailureService = operationsOutboxFailureService;
    }

    @GetMapping("/outbox-failures")
    public OperationsOutboxFailureListResponse getOutboxFailures(
            @RequestParam(required = false) Integer limit
    ) {
        return operationsOutboxFailureService.getFailedOutboxEvents(limit);
    }
}