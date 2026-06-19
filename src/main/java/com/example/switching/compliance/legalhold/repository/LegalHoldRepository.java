package com.example.switching.compliance.legalhold.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.switching.compliance.legalhold.entity.LegalHoldEntity;
import com.example.switching.compliance.legalhold.entity.LegalHoldStatus;

import jakarta.persistence.LockModeType;

public interface LegalHoldRepository extends JpaRepository<LegalHoldEntity, Long> {
    Optional<LegalHoldEntity> findByHoldRef(String holdRef);
    List<LegalHoldEntity> findByStatusOrderByRequestedAtDesc(LegalHoldStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from LegalHoldEntity h where h.id = :id")
    Optional<LegalHoldEntity> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select h from LegalHoldEntity h
            where h.status in (
                    com.example.switching.compliance.legalhold.entity.LegalHoldStatus.ACTIVE,
                    com.example.switching.compliance.legalhold.entity.LegalHoldStatus.RELEASE_REQUESTED
                  )
              and h.scopeType = com.example.switching.compliance.legalhold.entity.LegalHoldScopeType.TABLE
              and (h.scopeKey = :tableName or h.scopeKey = '*')
              and (h.effectiveFrom is null or h.effectiveFrom <= :businessDate)
              and (h.effectiveTo is null or h.effectiveTo >= :businessDate)
            order by h.requestedAt asc, h.id asc
            """)
    List<LegalHoldEntity> findBlockingTableHolds(@Param("tableName") String tableName,
                                                 @Param("businessDate") LocalDate businessDate,
                                                 Pageable pageable);
}
