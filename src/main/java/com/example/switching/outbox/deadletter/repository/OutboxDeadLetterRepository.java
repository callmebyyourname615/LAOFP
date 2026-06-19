package com.example.switching.outbox.deadletter.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.switching.outbox.deadletter.entity.DeadLetterStatus;
import com.example.switching.outbox.deadletter.entity.OutboxDeadLetterEntity;

import jakarta.persistence.LockModeType;

public interface OutboxDeadLetterRepository extends JpaRepository<OutboxDeadLetterEntity, Long> {
    Optional<OutboxDeadLetterEntity> findByEventId(String eventId);
    List<OutboxDeadLetterEntity> findByStatusOrderByLastFailedAtDesc(DeadLetterStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from OutboxDeadLetterEntity d where d.id = :id")
    Optional<OutboxDeadLetterEntity> findByIdForUpdate(@Param("id") Long id);
}
