package com.example.switching.operations.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.operations.dto.OperationsAuditLogListResponse;
import com.example.switching.operations.service.OperationsAuditLogQueryService;

@RestController
@RequestMapping("/api/operations")
public class OperationsAuditLogQueryController {

    private final OperationsAuditLogQueryService auditLogQueryService;

    public OperationsAuditLogQueryController(
            OperationsAuditLogQueryService auditLogQueryService
    ) {
        this.auditLogQueryService = auditLogQueryService;
    }

    @GetMapping("/audit-logs")
    public OperationsAuditLogListResponse searchAuditLogs(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String referenceType,
            @RequestParam(required = false) String referenceId,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String transferRef,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String messageId,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) Boolean includePayload,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset
    ) {
        return auditLogQueryService.searchAuditLogs(
                eventType,
                referenceType,
                referenceId,
                actor,
                transferRef,
                requestId,
                messageId,
                fromDate,
                toDate,
                includePayload,
                limit,
                offset
        );
    }
}