package com.example.switching.settlement.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.switching.settlement.entity.SettlementInstructionEntity;

public interface SettlementInstructionRepository extends JpaRepository<SettlementInstructionEntity, Long> {

    Optional<SettlementInstructionEntity> findByInstructionRef(String instructionRef);

    Optional<SettlementInstructionEntity> findByRtgsMsgId(String rtgsMsgId);

    Optional<SettlementInstructionEntity> findByTransferRef(String transferRef);

    List<SettlementInstructionEntity> findByCycleIdOrderByInstructionRefAsc(Long cycleId);

    List<SettlementInstructionEntity> findByStatusOrderByCreatedAtAsc(String status);

    boolean existsByCycleId(Long cycleId);
}
