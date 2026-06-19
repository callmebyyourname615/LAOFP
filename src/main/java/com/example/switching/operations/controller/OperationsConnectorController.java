package com.example.switching.operations.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.operations.dto.OperationsConnectorHealthListResponse;
import com.example.switching.operations.dto.OperationsConnectorTestResponse;
import com.example.switching.operations.service.OperationsConnectorHealthService;
import com.example.switching.operations.service.OperationsConnectorTestService;

@RestController
@RequestMapping("/api/operations/connectors")
public class OperationsConnectorController {

    private final OperationsConnectorHealthService connectorHealthService;
    private final OperationsConnectorTestService connectorTestService;

    public OperationsConnectorController(
            OperationsConnectorHealthService connectorHealthService,
            OperationsConnectorTestService connectorTestService
    ) {
        this.connectorHealthService = connectorHealthService;
        this.connectorTestService = connectorTestService;
    }

    @GetMapping("/health")
    public OperationsConnectorHealthListResponse getConnectorHealth(
            @RequestParam(required = false) String connectorName
    ) {
        return connectorHealthService.getConnectorHealth(connectorName);
    }

    @PostMapping("/{connectorName}/test")
    public OperationsConnectorTestResponse testConnector(
            @PathVariable String connectorName
    ) {
        return connectorTestService.testConnector(connectorName);
    }
}