package com.example.switching.audit.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.audit.dto.AuditLogListResponse;
import com.example.switching.audit.service.AuditLogQueryService;

@RestController
public class AuditLogController {

    private final AuditLogQueryService auditLogQueryService;

    public AuditLogController(AuditLogQueryService auditLogQueryService) {
        this.auditLogQueryService = auditLogQueryService;
    }

    @GetMapping("/api/audit-logs")
    public ResponseEntity<AuditLogListResponse> searchAuditLogs(
            @RequestParam(value = "eventType", required = false) String eventType,
            @RequestParam(value = "referenceType", required = false) String referenceType,
            @RequestParam(value = "referenceId", required = false) String referenceId,
            @RequestParam(value = "actor", required = false) String actor,
            @RequestParam(value = "limit", required = false) Integer limit) {

        AuditLogListResponse response = auditLogQueryService.search(
                eventType,
                referenceType,
                referenceId,
                actor,
                limit
        );

        return ResponseEntity.ok(response);
    }
}