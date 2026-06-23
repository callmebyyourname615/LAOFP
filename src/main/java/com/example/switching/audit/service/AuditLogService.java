package com.example.switching.audit.service;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.audit.entity.AuditLogEntity;
import com.example.switching.audit.repository.AuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.switching.security.util.SensitiveDataSanitizer;
import com.example.switching.observability.tracing.TraceContextSupport;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private static final long AUDIT_CHAIN_LOCK = 83749201L;
    private final ObjectMapper objectMapper;
    private final SensitiveDataSanitizer sanitizer;
    private final JdbcTemplate jdbcTemplate;
    private final TraceContextSupport traceContext;

    public AuditLogService(AuditLogRepository auditLogRepository,
                           ObjectMapper objectMapper,
                           SensitiveDataSanitizer sanitizer,
                           JdbcTemplate jdbcTemplate,
                           TraceContextSupport traceContext) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
        this.sanitizer = sanitizer;
        this.jdbcTemplate = jdbcTemplate;
        this.traceContext = traceContext;
    }

    @Transactional
    public AuditLogEntity log(String eventType,
                              String referenceType,
                              String referenceId,
                              String actor,
                              Object payload) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setEventType(eventType);
        entity.setReferenceType(referenceType);
        entity.setReferenceId(referenceId);
        entity.setActor(actor);
        entity.setPayload(sanitizer.sanitizeJson(toJson(payload)));
        entity.setCreatedAt(LocalDateTime.now());
        entity.setTraceId(traceContext.currentTraceId().orElse(null));

        return saveChained(entity);
    }

    @Transactional
    public AuditLogEntity log(String eventType,
                              String referenceType,
                              String referenceId,
                              Object payload) {
        return log(eventType, referenceType, referenceId, "SYSTEM", payload);
    }

    @Transactional
    public AuditLogEntity logError(String eventType,
                                   String referenceType,
                                   String referenceId,
                                   String actor,
                                   Exception exception) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setEventType(eventType);
        entity.setReferenceType(referenceType);
        entity.setReferenceId(referenceId);
        entity.setActor(actor);
        entity.setPayload(sanitizer.sanitizeJson(buildErrorPayload(exception)));
        entity.setCreatedAt(LocalDateTime.now());
        entity.setTraceId(traceContext.currentTraceId().orElse(null));

        return saveChained(entity);
    }


    private AuditLogEntity saveChained(AuditLogEntity entity) {
        jdbcTemplate.execute("SELECT pg_advisory_xact_lock(" + AUDIT_CHAIN_LOCK + ")");
        String previous = auditLogRepository.findTopByEntryHashIsNotNullOrderByIdDesc()
                .map(AuditLogEntity::getEntryHash)
                .orElse("GENESIS");
        entity.setPreviousHash(previous);
        entity.setEntryHash(sha256(String.join("|",
                safe(entity.getEventType()),
                safe(entity.getReferenceType()),
                safe(entity.getReferenceId()),
                safe(entity.getActor()),
                safe(entity.getPayload()),
                safe(entity.getTraceId()),
                previous)));
        return auditLogRepository.save(entity);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private String toJson(Object payload) {
        if (payload == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{\"error\":\"payload_serialization_failed\",\"message\":\""
                    + safe(ex.getMessage())
                    + "\"}";
        }
    }

    private String buildErrorPayload(Exception exception) {
        String exceptionClass = exception == null ? "UnknownException" : exception.getClass().getSimpleName();
        String message = exception == null ? null : exception.getMessage();

        return "{\"error\":true,\"exception\":\""
                + safe(exceptionClass)
                + "\",\"message\":\""
                + safe(message)
                + "\"}";
    }

    private String safe(String value) {
        if (value == null) {
            return "";
        }

        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ");
    }
}
