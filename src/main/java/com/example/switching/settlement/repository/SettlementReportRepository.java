package com.example.switching.settlement.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.switching.settlement.entity.SettlementReportEntity;

public interface SettlementReportRepository extends JpaRepository<SettlementReportEntity, Long> {

    Optional<SettlementReportEntity> findByCycleIdAndPspIdAndReportType(
            Long cycleId, String pspId, String reportType);

    Optional<SettlementReportEntity> findByReportRef(String reportRef);

    List<SettlementReportEntity> findByCycleIdOrderByPspIdAsc(Long cycleId);

    List<SettlementReportEntity> findByPspIdOrderByGeneratedAtDesc(String pspId);
}
