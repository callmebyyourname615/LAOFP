package com.example.switching.outbox.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.switching.outbox.entity.PspSuspensionLogEntity;

public interface PspSuspensionLogRepository extends JpaRepository<PspSuspensionLogEntity, Long> {

    List<PspSuspensionLogEntity> findByPspIdOrderBySuspendedAtDesc(String pspId);

    long countByPspId(String pspId);
}
