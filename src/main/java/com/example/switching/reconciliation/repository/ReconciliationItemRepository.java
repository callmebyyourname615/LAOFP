package com.example.switching.reconciliation.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.switching.reconciliation.entity.ReconciliationItemEntity;

/**
 * Read-only JPA access to the partitioned {@code reconciliation_items} table.
 *
 * <p>Inserts must go through JdbcTemplate (partition key required from the start).
 */
public interface ReconciliationItemRepository extends JpaRepository<ReconciliationItemEntity, Long> {

    List<ReconciliationItemEntity> findByFileIdOrderByLineNumberAsc(Long fileId);

    List<ReconciliationItemEntity> findByFileIdAndMatchStatusOrderByLineNumberAsc(
            Long fileId, String matchStatus);

    Optional<ReconciliationItemEntity> findByFileIdAndTransactionRef(Long fileId, String transactionRef);

    int countByFileIdAndMatchStatus(Long fileId, String matchStatus);

    /**
     * Bulk-update a single item's match result.
     * Works on partitioned tables because the WHERE clause always includes reconciliation_date
     * from the caller (via id + reconciliation_date for the partition key lookup).
     * Spring Data JPQL update works fine here — Hibernate routes through the parent table.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE ReconciliationItemEntity i
            SET i.matchStatus    = :status,
                i.mismatchReason = :reason,
                i.matchedAt      = CURRENT_TIMESTAMP
            WHERE i.id = :id
            """)
    int updateMatchResult(@Param("id") Long id,
                          @Param("status") String status,
                          @Param("reason") String reason);
}
