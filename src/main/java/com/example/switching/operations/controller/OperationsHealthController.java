package com.example.switching.operations.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.operations.dto.OperationsHealthResponse;
import com.example.switching.operations.service.OperationsHealthService;

@RestController
@RequestMapping("/api/operations")
public class OperationsHealthController {

    private final OperationsHealthService operationsHealthService;

    public OperationsHealthController(OperationsHealthService operationsHealthService) {
        this.operationsHealthService = operationsHealthService;
    }

    @GetMapping("/health")
    public OperationsHealthResponse getHealth() {
        return operationsHealthService.getHealth();
    }
}