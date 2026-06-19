package com.example.switching.configchange.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.switching.configchange.entity.ConfigurationChangeRequestEntity;
import com.example.switching.configchange.entity.ConfigurationChangeStatus;

import jakarta.persistence.LockModeType;

public interface ConfigurationChangeRequestRepository extends JpaRepository<ConfigurationChangeRequestEntity, Long> {
    List<ConfigurationChangeRequestEntity> findTop100ByStatusOrderByRequestedAtDesc(ConfigurationChangeStatus status);

    List<ConfigurationChangeRequestEntity> findByStatusInAndExpiresAtBeforeOrderByExpiresAtAsc(
            Collection<ConfigurationChangeStatus> statuses,
            LocalDateTime expiresAt,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ConfigurationChangeRequestEntity c where c.id = :id")
    Optional<ConfigurationChangeRequestEntity> findByIdForUpdate(@Param("id") Long id);
}
