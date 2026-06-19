package com.example.switching.audit.repository;

import java.time.LocalDateTime;

public interface AuditLogSearchRow {

    Long getId();

    String getEventType();

    String getReferenceType();

    String getReferenceId();

    String getActor();

    String getPayload();

    LocalDateTime getCreatedAt();
}