package com.example.switching.settlement.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.switching.settlement.entity.SettlementCycleEntity;

public interface SettlementCycleRepository extends JpaRepository<SettlementCycleEntity, Long> {

    Optional<SettlementCycleEntity> findByCycleRef(String cycleRef);

    List<SettlementCycleEntity> findBySettlementDateOrderByCycleNumberAsc(LocalDate date);

    List<SettlementCycleEntity> findByStatusOrderBySettlementDateDescIdDesc(String status);

    boolean existsByCycleRef(String cycleRef);

    Optional<SettlementCycleEntity> findFirstBySettlementDateAndStatusOrderByCycleNumberDesc(
            LocalDate date, String status);

    int countBySettlementDate(LocalDate date);
}
