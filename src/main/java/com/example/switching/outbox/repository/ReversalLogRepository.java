package com.example.switching.outbox.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.switching.outbox.entity.ReversalLogEntity;

public interface ReversalLogRepository extends JpaRepository<ReversalLogEntity, Long> {

    /** Count reversals for a destination bank within a rolling time window. */
    @Query("""
        SELECT COUNT(r) FROM ReversalLogEntity r
        WHERE r.destinationBank = :bank
          AND r.triggeredAt >= :since
        """)
    long countByDestinationBankAndTriggeredAtAfter(
            @Param("bank") String bank,
            @Param("since") LocalDateTime since);

    boolean existsByOriginalTxnId(String originalTxnId);
}
