package com.example.switching.settlement.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.switching.settlement.entity.SettlementPositionEntity;

public interface SettlementPositionRepository extends JpaRepository<SettlementPositionEntity, Long> {

    List<SettlementPositionEntity> findByCycleIdOrderByBankCodeAsc(Long cycleId);

    Optional<SettlementPositionEntity> findByCycleIdAndBankCodeAndCurrency(
            Long cycleId, String bankCode, String currency);

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE SettlementPositionEntity p
            SET p.status = 'SETTLED', p.settledAt = CURRENT_TIMESTAMP, p.updatedAt = CURRENT_TIMESTAMP
            WHERE p.cycleId = :cycleId
            """)
    int markAllSettledByCycleId(@Param("cycleId") Long cycleId);
}
