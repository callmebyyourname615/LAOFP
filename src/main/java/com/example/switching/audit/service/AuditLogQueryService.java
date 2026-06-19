package com.example.switching.audit.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.switching.audit.dto.AuditLogItemResponse;
import com.example.switching.audit.dto.AuditLogListResponse;
import com.example.switching.audit.repository.AuditLogRepository;
import com.example.switching.audit.repository.AuditLogSearchRow;

@Service
public class AuditLogQueryService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final AuditLogRepository auditLogRepository;

    public AuditLogQueryService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public AuditLogListResponse search(String eventType,
                                       String referenceType,
                                       String referenceId,
                                       String actor,
                                       Integer limit) {
        int resolvedLimit = resolveLimit(limit);

        String resolvedEventType = normalize(eventType);
        String resolvedReferenceType = normalize(referenceType);
        String resolvedReferenceId = normalize(referenceId);
        String resolvedActor = normalize(actor);

        List<AuditLogSearchRow> rows = auditLogRepository.searchAuditLogs(
                resolvedEventType,
                resolvedReferenceType,
                resolvedReferenceId,
                resolvedActor,
                resolvedLimit
        );

        List<AuditLogItemResponse> items = rows.stream()
                .map(this::toItem)
                .toList();

        return new AuditLogListResponse(
                items.size(),
                resolvedLimit,
                resolvedEventType,
                resolvedReferenceType,
                resolvedReferenceId,
                resolvedActor,
                items
        );
    }

    private AuditLogItemResponse toItem(AuditLogSearchRow row) {
        return new AuditLogItemResponse(
                row.getId(),
                row.getEventType(),
                row.getReferenceType(),
                row.getReferenceId(),
                row.getActor(),
                row.getPayload(),
                row.getCreatedAt()
        );
    }

    private int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}