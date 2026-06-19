package com.example.switching.security.breakglass.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.switching.security.breakglass.entity.PrivilegedAccessSessionEntity;
import com.example.switching.security.breakglass.entity.PrivilegedAccessStatus;

import jakarta.persistence.LockModeType;

public interface PrivilegedAccessSessionRepository extends JpaRepository<PrivilegedAccessSessionEntity, Long> {
    List<PrivilegedAccessSessionEntity> findTop100ByStatusOrderByRequestedAtDesc(PrivilegedAccessStatus status);

    List<PrivilegedAccessSessionEntity> findByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
            PrivilegedAccessStatus status, LocalDateTime expiresAt, Pageable pageable);

    List<PrivilegedAccessSessionEntity> findByStatusAndRequestedAtBeforeOrderByRequestedAtAsc(
            PrivilegedAccessStatus status, LocalDateTime requestedAt, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PrivilegedAccessSessionEntity s where s.id = :id")
    Optional<PrivilegedAccessSessionEntity> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PrivilegedAccessSessionEntity s where s.tokenHash = :tokenHash")
    Optional<PrivilegedAccessSessionEntity> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);
}
