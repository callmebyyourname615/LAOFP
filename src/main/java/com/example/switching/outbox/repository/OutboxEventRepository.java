package com.example.switching.outbox.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.switching.outbox.entity.OutboxEventEntity;
import com.example.switching.outbox.enums.OutboxStatus;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {

        List<OutboxEventEntity> findTop20ByStatusOrderByIdAsc(OutboxStatus status);

        @Query("""
                        select e from OutboxEventEntity e
                         where e.status = :status
                           and (e.nextRetryAt is null or e.nextRetryAt <= :now)
                         order by e.id asc
                        """)
        List<OutboxEventEntity> findPendingBatch(
                        @Param("status") OutboxStatus status,
                        @Param("now") java.time.LocalDateTime now,
                        Pageable pageable);

        List<OutboxEventEntity> findAllByTransferRefOrderByIdAsc(String transferRef);
        List<OutboxEventEntity> findByTransferRefOrderByCreatedAtAsc(String transferRef);

        long countByStatus(OutboxStatus status);

        @Query("""
                        select e
                          from OutboxEventEntity e
                         where (:status is null or e.status = :status)
                           and (:transferRef is null or e.transferRef = :transferRef)
                         order by e.id desc
                        """)
        List<OutboxEventEntity> searchOutboxEvents(
                        @Param("status") OutboxStatus status,
                        @Param("transferRef") String transferRef,
                        Pageable pageable);

        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query("""
                        update OutboxEventEntity e
                           set e.status = :nextStatus
                         where e.id = :id
                           and e.status = :currentStatus
                        """)
        int claimPendingEvent(@Param("id") Long id,
                        @Param("currentStatus") OutboxStatus currentStatus,
                        @Param("nextStatus") OutboxStatus nextStatus);

        @Query(value = """
                        select *
                          from outbox_messages
                         where status = :processingStatus
                           and updated_at < :cutoff
                         order by id asc
                         limit :limit
                        """, nativeQuery = true)
        List<OutboxEventEntity> findStuckProcessingEvents(
                        @Param("processingStatus") String processingStatus,
                        @Param("cutoff") LocalDateTime cutoff,
                        @Param("limit") int limit);

        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query(value = """
                        update outbox_messages
                           set status = :pendingStatus,
                               retry_count = :retryCount,
                               failure_class = 'TRANSIENT',
                               will_retry = true,
                               updated_at = now()
                         where id = :id
                           and status = :processingStatus
                        """, nativeQuery = true)
        int recoverProcessingEvent(
                        @Param("id") Long id,
                        @Param("processingStatus") String processingStatus,
                        @Param("pendingStatus") String pendingStatus,
                        @Param("retryCount") int retryCount);

        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query(value = """
                        update outbox_messages
                           set status = :failedStatus,
                               retry_count = :retryCount,
                               failure_class = 'TRANSIENT',
                               will_retry = false,
                               next_retry_at = null,
                               updated_at = now()
                         where id = :id
                           and status = :processingStatus
                        """, nativeQuery = true)
        int markProcessingEventAsFailed(
                        @Param("id") Long id,
                        @Param("processingStatus") String processingStatus,
                        @Param("failedStatus") String failedStatus,
                        @Param("retryCount") int retryCount);

        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query(value = """
                        update outbox_messages
                           set status = :pendingStatus,
                               failure_class = null,
                               will_retry = false,
                               updated_at = now()
                         where id = :id
                           and status = :failedStatus
                        """, nativeQuery = true)
        int resetFailedEventToPending(
                        @Param("id") Long id,
                        @Param("failedStatus") String failedStatus,
                        @Param("pendingStatus") String pendingStatus);
}
