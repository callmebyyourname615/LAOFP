package com.example.switching.connector.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.connector.dto.ConnectorConfigListResponse;
import com.example.switching.connector.dto.ConnectorConfigResponse;
import com.example.switching.connector.dto.CreateConnectorConfigRequest;
import com.example.switching.connector.dto.UpdateConnectorConfigRequest;
import com.example.switching.connector.service.ConnectorConfigManagementService;
import com.example.switching.connector.service.ConnectorConfigService;

@RestController
@RequestMapping("/api/connector-configs")
public class ConnectorConfigController {

    private final ConnectorConfigService connectorConfigService;
    private final ConnectorConfigManagementService connectorConfigManagementService;

    public ConnectorConfigController(ConnectorConfigService connectorConfigService,
                                     ConnectorConfigManagementService connectorConfigManagementService) {
        this.connectorConfigService = connectorConfigService;
        this.connectorConfigManagementService = connectorConfigManagementService;
    }

    // ── READ ──────────────────────────────────────────────────────────────────

    @GetMapping
    public ConnectorConfigListResponse list() {
        return connectorConfigService.list();
    }

    @GetMapping("/{connectorName}")
    public ConnectorConfigResponse getByConnectorName(
            @PathVariable String connectorName) {
        return connectorConfigService.getByConnectorName(connectorName);
    }

    // ── WRITE ─────────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<ConnectorConfigResponse> create(
            @RequestBody CreateConnectorConfigRequest request) {
        ConnectorConfigResponse response = connectorConfigManagementService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{connectorName}")
    public ConnectorConfigResponse update(
            @PathVariable String connectorName,
            @RequestBody UpdateConnectorConfigRequest request) {
        return connectorConfigManagementService.update(connectorName, request);
    }
}
