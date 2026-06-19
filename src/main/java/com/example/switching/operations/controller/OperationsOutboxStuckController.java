package com.example.switching.operations.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.operations.dto.OperationsOutboxStuckListResponse;
import com.example.switching.operations.service.OperationsOutboxStuckService;

@RestController
@RequestMapping("/api/operations")
public class OperationsOutboxStuckController {

    private final OperationsOutboxStuckService operationsOutboxStuckService;

    public OperationsOutboxStuckController(
            OperationsOutboxStuckService operationsOutboxStuckService
    ) {
        this.operationsOutboxStuckService = operationsOutboxStuckService;
    }

    @GetMapping("/outbox-stuck")
    public OperationsOutboxStuckListResponse getStuckOutboxEvents(
            @RequestParam(required = false) Integer minutes,
            @RequestParam(required = false) Integer limit
    ) {
        return operationsOutboxStuckService.getStuckOutboxEvents(minutes, limit);
    }
}


