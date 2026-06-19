package com.example.switching.audit.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.switching.audit.entity.AuditLogEntity;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {

    java.util.Optional<AuditLogEntity> findTopByEntryHashIsNotNullOrderByIdDesc();

    @Query(
            value = """
                    select
                        id as id,
                        event_type as eventType,
                        reference_type as referenceType,
                        reference_id as referenceId,
                        actor as actor,
                        payload as payload,
                        created_at as createdAt
                    from audit_logs
                    where (:eventType is null or event_type = :eventType)
                      and (:referenceType is null or reference_type = :referenceType)
                      and (:referenceId is null or reference_id = :referenceId)
                      and (:actor is null or actor = :actor)
                    order by id desc
                    limit :limit
                    """,
            nativeQuery = true
    )
    List<AuditLogSearchRow> searchAuditLogs(
            @Param("eventType") String eventType,
            @Param("referenceType") String referenceType,
            @Param("referenceId") String referenceId,
            @Param("actor") String actor,
            @Param("limit") int limit
    );

    @Query(
            value = """
                    select
                        id as id,
                        event_type as eventType,
                        reference_type as referenceType,
                        reference_id as referenceId,
                        actor as actor,
                        payload as payload,
                        created_at as createdAt
                    from audit_logs
                    where reference_type = :referenceType
                      and reference_id = :referenceId
                    order by id asc
                    """,
            nativeQuery = true
    )
    List<AuditLogSearchRow> findTraceAuditLogs(
            @Param("referenceType") String referenceType,
            @Param("referenceId") String referenceId
    );
    List<AuditLogEntity> findByReferenceTypeAndReferenceIdOrderByCreatedAtAsc(
        String referenceType,
        String referenceId
);
}